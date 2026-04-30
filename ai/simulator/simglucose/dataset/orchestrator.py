"""High-level orchestration for dataset generation.

Used by both the CLI (generate_dataset.py) and the Streamlit app.

Two generation modes:
  - random_pattern: each persona gets one randomly-sampled meal_pattern.
                    Plan is (category, count) — total sims = sum(counts).
  - full_matrix:    each persona is replicated across ALL 11 meal_patterns.
                    Plan is (category, n_blueprints) — total sims = sum × 11.
                    Same `persona_blueprint_id` ties the 11 rows together.

Supports sequential (n_workers=1) or multi-process (n_workers>1) execution.
Per-task seeds are derived deterministically from the master seed before
dispatch, so n_workers does not affect output content.
"""

import os
from multiprocessing import get_context

import numpy as np
import pandas as pd

from .persona_sampler import sample_persona, category_id
from .scenario_sampler import sample_scenario, MEAL_PATTERNS
from .simulator_runner import simulate
from .unified_schema import to_glucose_readings, to_meal_events, to_user_row


SIM_HOURS_PER_PERSONA = 24


# ─────────────────────────────────────────────────────────────────────────────
# Workers (module-level so multiprocessing can pickle them)
# ─────────────────────────────────────────────────────────────────────────────

def _run_one_simulation(args):
    """Worker: one (blueprint × meal_pattern) simulation.

    Args (tuple):
        category, persona_idx, meal_pattern, user_id, blueprint_seed, sim_seed
        (`meal_pattern` may be None → use whatever sample_persona gives back)

    Returns dict (same shape regardless of mode).
    """
    category, persona_idx, meal_pattern, user_id, blueprint_seed, sim_seed = args

    # 1. Persona blueprint (continuous values determined by blueprint_seed only)
    rng_bp = np.random.default_rng(blueprint_seed)
    persona = sample_persona(category, rng_bp)

    # 2. Override meal_pattern if explicitly requested (full_matrix mode)
    if meal_pattern is not None:
        persona["meal_pattern"] = meal_pattern
    pattern = persona["meal_pattern"]

    # 3. Scenario + simulation use sim_seed (independent from blueprint)
    rng_sim = np.random.default_rng(sim_seed)
    scenario = sample_scenario(rng_sim, meal_pattern=pattern)

    try:
        sim_df = simulate(
            persona=persona,
            meal_events=scenario,
            sim_hours=SIM_HOURS_PER_PERSONA,
            cgm_seed=int(rng_sim.integers(0, 2**31 - 1)),
        )
    except Exception as e:
        return {
            "success": False,
            "category": category,
            "persona_idx": persona_idx,
            "meal_pattern": pattern,
            "user_id": user_id,
            "persona": persona,
            "error": str(e),
        }

    label = category_id(category)
    _, _, fbg_bin, weight_bin = category
    blueprint_id = f"bp_{label}_{persona_idx:02d}"

    user_row = to_user_row(persona, user_id)
    user_row["fbg_bin"] = fbg_bin
    user_row["weight_bin"] = weight_bin
    user_row["persona_blueprint_id"] = blueprint_id
    user_row["category"] = label

    return {
        "success": True,
        "category": category,
        "persona_idx": persona_idx,
        "meal_pattern": pattern,
        "user_id": user_id,
        "persona": persona,
        "glucose": to_glucose_readings(sim_df, user_id),
        "meal":    to_meal_events(scenario, user_id),
        "user_row": user_row,
    }


# ─────────────────────────────────────────────────────────────────────────────
# Task builders — translate (mode, plan) into worker arg tuples
# ─────────────────────────────────────────────────────────────────────────────

def _build_random_pattern_tasks(plan, seed, user_id_offset):
    """One sim per persona; meal_pattern picked inside sample_persona."""
    plan = [(c, int(n)) for c, n in plan if int(n) > 0]
    total = sum(n for _, n in plan)

    master = np.random.default_rng(seed)
    seeds = master.integers(0, 2**31 - 1, size=total).tolist()

    tasks = []
    idx = 0
    for category, n in plan:
        for persona_idx in range(n):
            user_id = f"sim_{user_id_offset + idx:05d}"
            s = int(seeds[idx])
            # Use the same seed for blueprint and sim so behavior matches the
            # legacy single-rng path where one rng drove everything.
            tasks.append((category, persona_idx, None, user_id, s, s))
            idx += 1
    return tasks, total


def _build_full_matrix_tasks(plan, seed, user_id_offset):
    """Replicate each blueprint across all 11 meal_patterns."""
    plan = [(c, int(n)) for c, n in plan if int(n) > 0]
    total = sum(n for _, n in plan) * len(MEAL_PATTERNS)

    master = np.random.default_rng(seed)

    tasks = []
    idx = 0
    for category, n in plan:
        # Each blueprint gets its own deterministic seed so its continuous
        # values are stable across the 11 pattern siblings.
        bp_seeds = master.integers(0, 2**31 - 1, size=n).tolist()
        for persona_idx in range(n):
            blueprint_seed = int(bp_seeds[persona_idx])
            # Per-(blueprint, pattern) sim seed = derived deterministically.
            rng_per_bp = np.random.default_rng(blueprint_seed + 1)
            sim_seeds = rng_per_bp.integers(
                0, 2**31 - 1, size=len(MEAL_PATTERNS)
            ).tolist()
            for pat_i, pattern in enumerate(MEAL_PATTERNS):
                user_id = f"sim_{user_id_offset + idx:05d}"
                tasks.append((
                    category,
                    persona_idx,
                    pattern,
                    user_id,
                    blueprint_seed,
                    int(sim_seeds[pat_i]),
                ))
                idx += 1
    return tasks, total


# ─────────────────────────────────────────────────────────────────────────────
# Public entry points
# ─────────────────────────────────────────────────────────────────────────────

def generate_from_plan(plan, seed, progress_callback=None,
                       user_id_offset=0, n_workers=1, mode="random_pattern"):
    """Generate a dataset.

    Args:
        plan: iterable of (category_tuple, count) pairs.
              random_pattern: count = number of personas (sims).
              full_matrix:    count = number of blueprints; total sims = ×11.
        seed: random seed (master)
        progress_callback: optional callable(current_idx, total, persona, category)
        user_id_offset: starting index for user_id generation
        n_workers: 1 = sequential, >1 = multiprocessing pool
        mode: "random_pattern" or "full_matrix"

    Returns:
        dict with keys 'glucose_df', 'meal_df', 'users_df', 'failures'
    """
    if mode == "full_matrix":
        tasks, total = _build_full_matrix_tasks(plan, seed, user_id_offset)
    elif mode == "random_pattern":
        tasks, total = _build_random_pattern_tasks(plan, seed, user_id_offset)
    else:
        raise ValueError(f"unknown mode {mode!r} (must be random_pattern or full_matrix)")

    if total == 0:
        raise RuntimeError("Empty plan: no personas to generate")

    glucose_chunks, meal_chunks, user_rows, failures = [], [], [], []

    def _accumulate(res, completed_idx):
        if res["success"]:
            glucose_chunks.append(res["glucose"])
            meal_chunks.append(res["meal"])
            user_rows.append(res["user_row"])
        else:
            failures.append({
                "user_id": res["user_id"],
                "category": category_id(res["category"]),
                "meal_pattern": res.get("meal_pattern"),
                "error": res["error"],
            })
        if progress_callback:
            progress_callback(completed_idx, total, res["persona"], res["category"])

    if n_workers <= 1:
        for i, args in enumerate(tasks, 1):
            res = _run_one_simulation(args)
            _accumulate(res, i)
    else:
        ctx = get_context("spawn")
        with ctx.Pool(n_workers) as pool:
            for i, res in enumerate(pool.imap_unordered(_run_one_simulation, tasks), 1):
                _accumulate(res, i)

    if not glucose_chunks:
        raise RuntimeError("No simulations succeeded")

    return {
        "glucose_df": pd.concat(glucose_chunks, ignore_index=True),
        "meal_df":    pd.concat(meal_chunks, ignore_index=True),
        "users_df":   pd.DataFrame(user_rows),
        "failures":   failures,
    }


# ─────────────────────────────────────────────────────────────────────────────
# Persistence
# ─────────────────────────────────────────────────────────────────────────────

def save_dataset(result, output_dir):
    """Write three flat CSVs (single combined dataset)."""
    os.makedirs(output_dir, exist_ok=True)
    paths = {
        "glucose_readings.csv": result["glucose_df"],
        "meal_events.csv":      result["meal_df"],
        "users.csv":            result["users_df"],
    }
    written = {}
    for name, df in paths.items():
        path = os.path.join(output_dir, name)
        df.to_csv(path, index=False)
        written[name] = path
    return written


def save_dataset_per_category(result, output_dir):
    """Write one folder per category, each with its own 3 CSVs.

    Layout:
        {output_dir}/{category_id}/users.csv
        {output_dir}/{category_id}/glucose_readings.csv
        {output_dir}/{category_id}/meal_events.csv
    """
    os.makedirs(output_dir, exist_ok=True)
    users_df   = result["users_df"]
    glucose_df = result["glucose_df"]
    meal_df    = result["meal_df"]

    written = {}
    for cat_label, users_chunk in users_df.groupby("category"):
        folder = os.path.join(output_dir, cat_label)
        os.makedirs(folder, exist_ok=True)
        user_ids = set(users_chunk["user_id"])

        glucose_chunk = glucose_df[glucose_df["user_id"].isin(user_ids)]
        meal_chunk    = meal_df[meal_df["user_id"].isin(user_ids)] if len(meal_df) else meal_df

        users_chunk.to_csv(os.path.join(folder, "users.csv"), index=False)
        glucose_chunk.to_csv(os.path.join(folder, "glucose_readings.csv"), index=False)
        meal_chunk.to_csv(os.path.join(folder, "meal_events.csv"), index=False)

        written[cat_label] = folder
    return written
