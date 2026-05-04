"""모든 모델 평가 + 비교표 자동 생성.

전제 (학습 완료):
    ai/models/{ridge_meal.pkl, mlp_meal.pt, lstm_meal.pt, lstm_now.pt}
    ai/models/scaler.pkl
    ai/data/processed/test.csv
    ai/data/processed/timeseries/test.npz

출력:
    ai/reports/scenarios/{M1-R, M1-MLP, M1-LSTM, M2-LSTM}_clarke_{30,60}min.png
    ai/reports/scenarios/{...}_curves.png
    ai/reports/final_comparison_table.png
    ai/reports/clarke_grid_meal_30min.png    (Model 1 3개 나란히)
    ai/reports/clarke_grid_now_30min.png     (Model 2 단일)

사용법:
    cd ai/
    python scripts/run_all_evaluations.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import torch
from torch.utils.data import DataLoader

# 경로 설정 (ai/ 루트로 import 가능하게)
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.glucose.constants import (
    DEFAULT_MODELS_DIR,
    DEFAULT_PROCESSED_DIR,
    DEFAULT_TIMESERIES_DIR,
    LABEL_STEPS,
)
from app.glucose.data_loader import (
    MealDataset,
    TimeseriesDataset,
    get_dataloader,
    load_scaler,
)
from scripts.evaluate import (
    baseline_last_value,
    baseline_meal_heuristic,
    baseline_now_last_value,
    evaluate_predictions,
    generate_comparison_table,
    plot_clarke_error_grid,
    plot_curve_examples,
)
from app.glucose.model import (
    MealLSTMDecoder,
    MealMLP,
    MealRidge,
    NowLSTM,
    load_torch_model,
)


KEY_HORIZONS = [30, 60, 120]


# ─────────────────────────────────────────────────────────────────────
# 추론
# ─────────────────────────────────────────────────────────────────────


def _inverse_bg(y_normalized: np.ndarray, bg_scaler: Any) -> np.ndarray:
    flat = y_normalized.reshape(-1, 1)
    return bg_scaler.inverse_transform(flat).reshape(y_normalized.shape)


def predict_meal_dataset(
    model_path: Path,
    model_type: str,
    test_csv: Path,
    bg_scaler: Any,
    device: torch.device,
) -> tuple[np.ndarray, np.ndarray]:
    """Model 1 추론 → (y_true_raw, y_pred_raw) [N, 24]."""
    ds = MealDataset(test_csv, return_categorical_separately=True)
    if model_type == "ridge":
        ridge = MealRidge.load(model_path)
        y_pred_norm = ridge.predict(ds.x_continuous, ds.x_categorical)
    else:
        model_class = MealMLP if model_type == "mlp" else MealLSTMDecoder
        model, _ = load_torch_model(model_path, model_class)
        model = model.to(device)
        model.eval()
        loader = get_dataloader(ds, batch_size=64, shuffle=False)
        preds: list[np.ndarray] = []
        with torch.no_grad():
            for (x_cont, x_cat), _ in loader:
                x_cont = x_cont.to(device)
                x_cat = x_cat.to(device)
                preds.append(model(x_cont, x_cat).cpu().numpy())
        y_pred_norm = np.concatenate(preds, axis=0)

    y_true_raw = _inverse_bg(ds.y, bg_scaler)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
    return y_true_raw, y_pred_raw


def predict_now_dataset(
    model_path: Path,
    test_npz: Path,
    bg_scaler: Any,
    device: torch.device,
) -> tuple[np.ndarray, np.ndarray]:
    """Model 2 추론."""
    ds = TimeseriesDataset(test_npz)
    model, _ = load_torch_model(model_path, NowLSTM)
    model = model.to(device)
    model.eval()
    loader = get_dataloader(ds, batch_size=64, shuffle=False)
    preds: list[np.ndarray] = []
    with torch.no_grad():
        for (x_seq, x_profile), _ in loader:
            x_seq = x_seq.to(device)
            x_profile = x_profile.to(device)
            preds.append(model(x_seq, x_profile).cpu().numpy())
    y_pred_norm = np.concatenate(preds, axis=0)
    y_true_raw = _inverse_bg(ds.y, bg_scaler)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
    return y_true_raw, y_pred_raw


# ─────────────────────────────────────────────────────────────────────
# 시나리오
# ─────────────────────────────────────────────────────────────────────


def run_scenario(
    scenario_id: str,
    model_path: Path,
    model_type: str,
    test_path: Path,
    bg_scaler: Any,
    reports_dir: Path,
    device: torch.device,
) -> dict[str, Any] | None:
    print(f"\n[{scenario_id}] {model_type}  test={test_path}")
    if not model_path.exists():
        print(f"  [WARNING] 모델 파일 없음: {model_path} - skip")
        return None
    if not test_path.exists():
        print(f"  [WARNING] 테스트 데이터 없음: {test_path} - skip")
        return None

    if model_type == "now_lstm":
        y_true, y_pred = predict_now_dataset(model_path, test_path, bg_scaler, device)
    else:
        y_true, y_pred = predict_meal_dataset(
            model_path, model_type, test_path, bg_scaler, device
        )

    result = evaluate_predictions(y_true, y_pred, scenario_id, model_type)
    print(
        f"  N={result['n_samples']}, "
        f"RMSE@30={result['rmse_per_horizon'][LABEL_STEPS.index(30)]:.2f}, "
        f"RMSE@60={result['rmse_per_horizon'][LABEL_STEPS.index(60)]:.2f}, "
        f"RMSE@120={result['rmse_per_horizon'][LABEL_STEPS.index(120)]:.2f}"
    )

    # Clarke EG (30, 60)
    scen_dir = reports_dir / "scenarios"
    scen_dir.mkdir(parents=True, exist_ok=True)
    for h in (30, 60):
        i = LABEL_STEPS.index(h)
        plot_clarke_error_grid(
            y_true[:, i],
            y_pred[:, i],
            horizon_min=h,
            save_path=scen_dir / f"{scenario_id}_clarke_{h}min.png",
            title_prefix=f"{scenario_id} — ",
        )

    # 곡선 예시
    plot_curve_examples(
        y_true,
        y_pred,
        save_path=scen_dir / f"{scenario_id}_curves.png",
        n_examples=5,
        title_prefix=f"{scenario_id} — ",
    )

    return result


def plot_combined_clarke(
    runs: list[tuple[str, np.ndarray, np.ndarray]],
    horizon_min: int,
    save_path: Path,
) -> None:
    """여러 모델의 같은 horizon Clarke EG 를 가로로 나란히."""
    if not runs:
        return
    n = len(runs)
    fig, axes = plt.subplots(1, n, figsize=(7 * n, 7), squeeze=False)
    for ax, (name, y_true, y_pred) in zip(axes[0], runs):
        i = LABEL_STEPS.index(horizon_min)
        # plot_clarke_error_grid 와 동일 로직 인라인 (재사용 위해 모듈 함수도 가능)
        from scripts.evaluate import _shade_zones, clarke_zone_counts

        ax.set_facecolor("white")
        _shade_zones(ax)
        ax.scatter(y_true[:, i], y_pred[:, i], c="black", alpha=0.3, s=10)
        ax.plot([0, 400], [0, 400], "--", color="gray", linewidth=0.8)
        ax.set_xlim(0, 400)
        ax.set_ylim(0, 400)
        ax.set_xlabel("Reference (mg/dL)")
        ax.set_ylabel("Predicted (mg/dL)")
        ax.set_title(f"{name} (PH={horizon_min}min)")
        ax.grid(True, linestyle=":", alpha=0.5)

        counts = clarke_zone_counts(y_true[:, i], y_pred[:, i])
        total = sum(counts.values()) or 1
        ratios = {z: 100 * counts[z] / total for z in "ABCDE"}
        text = "\n".join(f"{z}: {ratios[z]:.1f}%" for z in "ABCDE")
        text += f"\nA+B: {ratios['A'] + ratios['B']:.1f}%"
        ax.text(
            0.97,
            0.03,
            text,
            transform=ax.transAxes,
            ha="right",
            va="bottom",
            fontsize=9,
            bbox=dict(boxstyle="round,pad=0.3", facecolor="white", edgecolor="gray"),
        )
    save_path.parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────


# ─────────────────────────────────────────────────────────────────────
# Naive baseline 평가
# ─────────────────────────────────────────────────────────────────────


def evaluate_baseline_meal(
    test_csv: Path,
    bg_scaler: Any,
) -> tuple[
    tuple[np.ndarray, np.ndarray, dict],
    tuple[np.ndarray, np.ndarray, dict],
]:
    """Model 1 baseline 두 종 평가:
    - last_value: current_glucose 그대로 24개
    - heuristic: carbs 기반 식후 30분 피크
    """
    if not test_csv.exists():
        raise FileNotFoundError(test_csv)
    from app.glucose.constants import LABEL_COLS

    df = pd.read_csv(test_csv)
    # current_glucose, carbs 는 정규화 상태 → raw 로 inverse_transform
    feature_scaler = load_scaler()["model1_features"]
    # MODEL1_SCALE_COLS = [carbs, current_glucose, fasting_bg, weight_kg]
    cols_norm = df[["carbs", "current_glucose", "fasting_bg", "weight_kg"]].values
    cols_raw = feature_scaler.inverse_transform(cols_norm)
    carbs_raw = cols_raw[:, 0]
    current_glucose_raw = cols_raw[:, 1]

    y_true_norm = df[LABEL_COLS].values
    y_true_raw = bg_scaler.inverse_transform(y_true_norm.reshape(-1, 1)).reshape(y_true_norm.shape)

    # baseline 1: last_value
    y_pred_lv = baseline_last_value(current_glucose_raw)
    res_lv = evaluate_predictions(y_true_raw, y_pred_lv, "BASELINE-Last", "baseline")

    # baseline 2: heuristic
    y_pred_h = baseline_meal_heuristic(current_glucose_raw, carbs_raw)
    res_h = evaluate_predictions(y_true_raw, y_pred_h, "BASELINE-Heur", "baseline")

    return (y_true_raw, y_pred_lv, res_lv), (y_true_raw, y_pred_h, res_h)


def evaluate_baseline_now(
    test_npz: Path,
    bg_scaler: Any,
) -> tuple[np.ndarray, np.ndarray, dict]:
    """Model 2 baseline: 마지막 BG 그대로 24개."""
    if not test_npz.exists():
        raise FileNotFoundError(test_npz)
    data = np.load(test_npz)
    x_seq_norm = data["X_seq"]
    y_norm = data["y"]
    last_norm = x_seq_norm[:, -1]

    last_raw = bg_scaler.inverse_transform(last_norm.reshape(-1, 1)).flatten()
    y_true_raw = bg_scaler.inverse_transform(y_norm.reshape(-1, 1)).reshape(y_norm.shape)
    y_pred_raw = baseline_now_last_value(last_raw)

    res = evaluate_predictions(y_true_raw, y_pred_raw, "BASELINE-LastSeq", "baseline")
    return y_true_raw, y_pred_raw, res


# ─────────────────────────────────────────────────────────────────────
# 유형별 분리 평가
# ─────────────────────────────────────────────────────────────────────


def per_type_metrics(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    diabetes_types: np.ndarray,
    model_name: str,
) -> list[dict[str, Any]]:
    """diabetes_type 별로 metric 분리."""
    type_names = {0: "T1D", 1: "T2D", 2: "Normal"}
    out = []
    for t_int, t_name in type_names.items():
        mask = diabetes_types == t_int
        n = int(mask.sum())
        if n == 0:
            continue
        sub = evaluate_predictions(
            y_true[mask], y_pred[mask], f"{model_name}-{t_name}", "split"
        )
        out.append(sub)
    return out


def diabetes_types_for_meal(test_csv: Path) -> np.ndarray:
    df = pd.read_csv(test_csv)
    return df["diabetes_type"].to_numpy()


def diabetes_types_for_now(test_npz: Path) -> np.ndarray:
    """X_profile[:, 3] 이 diabetes_type."""
    data = np.load(test_npz)
    return data["X_profile"][:, 3].astype(int)


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────


def main() -> int:
    os.chdir(ROOT)

    models_dir = Path(DEFAULT_MODELS_DIR)
    reports_dir = Path("reports")
    test_csv = Path(DEFAULT_PROCESSED_DIR) / "test.csv"
    test_npz = Path(DEFAULT_TIMESERIES_DIR) / "test.npz"

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    print("\n[scaler 로드]")
    scaler = load_scaler()
    bg_scaler = scaler["bg_target"]

    scenarios_meal = [
        ("M1-R", models_dir / "ridge_meal.pkl", "ridge", test_csv),
        ("M1-MLP", models_dir / "mlp_meal.pt", "mlp", test_csv),
        ("M1-LSTM", models_dir / "lstm_meal.pt", "lstm", test_csv),
    ]
    scenarios_now = [
        ("M2-LSTM", models_dir / "lstm_now.pt", "now_lstm", test_npz),
    ]

    results: list[dict[str, Any]] = []
    per_type_results: list[dict[str, Any]] = []
    meal_runs: list[tuple[str, np.ndarray, np.ndarray]] = []
    now_runs: list[tuple[str, np.ndarray, np.ndarray]] = []

    # ── Naive Baselines (Model 1) ────────────────────────────────────
    print("\n=== Naive Baselines (Model 1) ===")
    if test_csv.exists():
        try:
            (yt_lv, yp_lv, res_lv), (yt_h, yp_h, res_h) = evaluate_baseline_meal(
                test_csv, bg_scaler
            )
            results.append(res_lv)
            results.append(res_h)
            print(f"  BASELINE-Last  RMSE@30={res_lv['rmse_per_horizon'][LABEL_STEPS.index(30)]:.2f}")
            print(f"  BASELINE-Heur  RMSE@30={res_h['rmse_per_horizon'][LABEL_STEPS.index(30)]:.2f}")
        except FileNotFoundError as e:
            print(f"  [WARNING] test_csv 없음 - skip: {e}")

    # ── Model 1 ──────────────────────────────────────────────────────
    print("\n=== Model 1 ===")
    diabetes_meal = None
    if test_csv.exists():
        diabetes_meal = diabetes_types_for_meal(test_csv)

    for sid, mpath, mtype, tpath in scenarios_meal:
        result = run_scenario(sid, mpath, mtype, tpath, bg_scaler, reports_dir, device)
        if result is None:
            continue
        results.append(result)
        y_true, y_pred = predict_meal_dataset(mpath, mtype, tpath, bg_scaler, device)
        meal_runs.append((sid, y_true, y_pred))
        # 유형별 분리
        if diabetes_meal is not None:
            per_type_results.extend(per_type_metrics(y_true, y_pred, diabetes_meal, sid))

    # ── Naive Baseline (Model 2) ─────────────────────────────────────
    print("\n=== Naive Baseline (Model 2) ===")
    if test_npz.exists():
        try:
            yt_b, yp_b, res_b = evaluate_baseline_now(test_npz, bg_scaler)
            results.append(res_b)
            print(f"  BASELINE-LastSeq RMSE@30={res_b['rmse_per_horizon'][LABEL_STEPS.index(30)]:.2f}")
        except FileNotFoundError as e:
            print(f"  [WARNING] test_npz 없음 - skip: {e}")

    # ── Model 2 ──────────────────────────────────────────────────────
    print("\n=== Model 2 ===")
    diabetes_now = None
    if test_npz.exists():
        diabetes_now = diabetes_types_for_now(test_npz)

    for sid, mpath, mtype, tpath in scenarios_now:
        result = run_scenario(sid, mpath, mtype, tpath, bg_scaler, reports_dir, device)
        if result is None:
            continue
        results.append(result)
        y_true, y_pred = predict_now_dataset(mpath, tpath, bg_scaler, device)
        now_runs.append((sid, y_true, y_pred))
        if diabetes_now is not None:
            per_type_results.extend(per_type_metrics(y_true, y_pred, diabetes_now, sid))

    if not results:
        print("\n[ERROR] 평가 결과 없음. 모델/데이터 확인.")
        return 1

    print("\n[비교 표 생성]")
    generate_comparison_table(results, reports_dir / "final_comparison_table.png")

    if per_type_results:
        print("[유형별 분리 표 생성]")
        generate_comparison_table(
            per_type_results, reports_dir / "comparison_per_diabetes_type.png"
        )

    if meal_runs:
        plot_combined_clarke(meal_runs, 30, reports_dir / "clarke_grid_meal_30min.png")
    if now_runs:
        plot_combined_clarke(now_runs, 30, reports_dir / "clarke_grid_now_30min.png")

    print("\n[산출물]")
    print(f"  reports/final_comparison_table.png")
    print(f"  reports/comparison_per_diabetes_type.png")
    print(f"  reports/scenarios/*.png")
    print(f"  reports/clarke_grid_meal_30min.png" if meal_runs else "")
    print(f"  reports/clarke_grid_now_30min.png" if now_runs else "")

    print("\n[요약]")
    print(f"  {'Scenario':<22} | RMSE@30  RMSE@60  RMSE@120")
    print(f"  {'-'*22}-+-{'-'*32}")
    for r in results:
        i30 = LABEL_STEPS.index(30)
        i60 = LABEL_STEPS.index(60)
        i120 = LABEL_STEPS.index(120)
        print(
            f"  {r['model_name']:<22} | "
            f"{r['rmse_per_horizon'][i30]:>7.2f}  "
            f"{r['rmse_per_horizon'][i60]:>7.2f}  "
            f"{r['rmse_per_horizon'][i120]:>7.2f}"
        )

    print("\n[완료] 평가 완료")
    return 0


if __name__ == "__main__":
    sys.exit(main())
