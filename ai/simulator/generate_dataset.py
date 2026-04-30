"""Generate synthetic CGM dataset from the simulator (stratified sampling).

Strategy: enumerate clinically-plausible persona categories defined by
(diabetes_type, activity, fbg_bin, weight_bin), then draw N personas per
category. Each persona is simulated for exactly one day.

Two modes:
  full_matrix    — each persona blueprint × all 11 meal_patterns
                   total sims = categories × per_category × 11
  random_pattern — each persona gets one randomly-sampled meal_pattern
                   total sims = categories × per_category

Default mode is full_matrix and per-category-folder output.

Usage:
    # Full matrix, 50 blueprints/category × 11 patterns × 108 cats = 59,400 sims
    python generate_dataset.py --per-category 50 --workers 4

    # Smoke test (5 blueprints × 11 = 5,940 sims, ~10 min with 4 workers)
    python generate_dataset.py --per-category 5 --workers 4

    # Legacy random_pattern mode (single pattern per persona)
    python generate_dataset.py --mode random_pattern --per-category 50

Output layout (default — `--per-category-folders`):
    {output_dir}/{category_id}/users.csv
    {output_dir}/{category_id}/glucose_readings.csv
    {output_dir}/{category_id}/meal_events.csv

Or with `--single-folder`: three flat CSVs at {output_dir}/.

See docs/DATASET_GENERATION.md for category definition rationale and
docs/CATEGORY_REFERENCE.md for the meal_pattern category list.
"""

import argparse
import os
from datetime import datetime

from simglucose.dataset import (
    enumerate_categories,
    category_id,
    generate_from_plan,
    save_dataset,
    save_dataset_per_category,
)
from simglucose.dataset.scenario_sampler import MEAL_PATTERNS


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["full_matrix", "random_pattern"],
                        default="full_matrix",
                        help="full_matrix: each persona × all 11 patterns. "
                             "random_pattern: one pattern per persona. "
                             "(default: full_matrix)")
    parser.add_argument("--per-category", "--per-cell", dest="per_category",
                        type=int, default=50,
                        help="Persona blueprints per category (default: 50). "
                             "In full_matrix mode total sims = this × 11.")
    parser.add_argument("--output-dir", default="data/simulator",
                        help="Output directory (default: data/simulator)")
    parser.add_argument("--single-folder", action="store_true",
                        help="Write flat CSVs to output_dir instead of "
                             "per-category subfolders.")
    parser.add_argument("--seed", type=int, default=0,
                        help="Random seed (default: 0)")
    parser.add_argument("--workers", type=int, default=1,
                        help="Parallel workers (default: 1 = sequential). "
                             f"This machine has {os.cpu_count()} CPUs.")
    args = parser.parse_args(argv)

    categories = enumerate_categories()
    plan = [(c, args.per_category) for c in categories]

    if args.mode == "full_matrix":
        total = len(categories) * args.per_category * len(MEAL_PATTERNS)
        print(f"Mode: full_matrix")
        print(f"  {len(categories)} categories × {args.per_category} blueprints × "
              f"{len(MEAL_PATTERNS)} meal_patterns = {total:,} simulations")
    else:
        total = len(categories) * args.per_category
        print(f"Mode: random_pattern")
        print(f"  {len(categories)} categories × {args.per_category} personas = "
              f"{total:,} simulations")
    print(f"  Workers: {args.workers}, seed: {args.seed}")
    print(f"  Output: {args.output_dir} "
          f"({'flat' if args.single_folder else 'per-category folders'})")
    print()

    def _on_progress(idx, total, persona, category):
        if idx % 100 == 0 or idx == total:
            print(f"[{idx:>5}/{total}] category={category_id(category)}  "
                  f"pattern={persona['meal_pattern']}",
                  flush=True)

    t0 = datetime.now()
    result = generate_from_plan(
        plan=plan,
        seed=args.seed,
        progress_callback=_on_progress,
        n_workers=args.workers,
        mode=args.mode,
    )

    if args.single_folder:
        paths = save_dataset(result, args.output_dir)
        print()
        print(f"Wrote {len(result['glucose_df']):,} glucose readings  → {paths['glucose_readings.csv']}")
        print(f"Wrote {len(result['meal_df']):,} meal events  → {paths['meal_events.csv']}")
        print(f"Wrote {len(result['users_df']):,} users  → {paths['users.csv']}")
    else:
        folders = save_dataset_per_category(result, args.output_dir)
        print()
        print(f"Wrote {len(folders)} category folders under {args.output_dir}/")
        print(f"  Total: {len(result['glucose_df']):,} glucose readings, "
              f"{len(result['meal_df']):,} meal events, "
              f"{len(result['users_df']):,} users")

    elapsed = (datetime.now() - t0).total_seconds()

    if result['failures']:
        print(f"({len(result['failures'])} simulations failed)")

    print("\n=== Type x Category coverage ===")
    print(result['users_df'].groupby("diabetes_type")["category"].nunique()
          .rename("categories_covered").to_string())
    print()
    print("=== meal_pattern distribution ===")
    print(result['users_df']["meal_pattern"].value_counts().to_string())
    print()
    print("=== Glucose summary ===")
    print(result['glucose_df'].groupby("source")["glucose"].describe().round(2).to_string())
    print(f"\nDone in {elapsed:.1f}s")


if __name__ == "__main__":
    main()
