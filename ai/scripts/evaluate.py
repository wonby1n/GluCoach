"""평가 함수.

EVAL_SPEC.md 와 동기.
- evaluate_model: 24 horizon RMSE/MAE + Clarke EG A+B 비율 + Peak 오차
- plot_clarke_error_grid: 1987 Clarke 영역 정의 직접 구현 (외부 라이브러리 X)
- plot_predictions_curve, plot_meal_response_examples
- generate_comparison_table

본 모듈은 모델/scaler 가 학습된 후에 의미 있음. 데이터 무관 부분(Clarke EG 영역
판정 로직, 시각화 함수)은 미리 작성됨.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt
import numpy as np

from app.glucose.constants import LABEL_STEPS

KEY_HORIZONS = [30, 60, 120]


# ─────────────────────────────────────────────────────────────────────
# Naive baselines (모델 없는 휴리스틱 — Ridge/MLP/LSTM 비교 기준)
# ─────────────────────────────────────────────────────────────────────


def baseline_last_value(current_glucose: np.ndarray) -> np.ndarray:
    """식사 후에도 current_glucose 그대로 24개.

    Args:
        current_glucose: [N] raw mg/dL
    Returns:
        [N, 24] raw mg/dL
    """
    n = current_glucose.shape[0]
    return np.tile(current_glucose.reshape(n, 1), (1, 24))


def baseline_meal_heuristic(
    current_glucose: np.ndarray,
    carbs: np.ndarray,
    peak_ratio: float = 0.6,
) -> np.ndarray:
    """식후 30분 피크, 90분 후 baseline 복귀 휴리스틱.

    Args:
        current_glucose: [N] raw mg/dL
        carbs: [N] raw g
        peak_ratio: peak = current + carbs * peak_ratio
    Returns:
        [N, 24] raw mg/dL
    """
    n = current_glucose.shape[0]
    peak = current_glucose + np.maximum(0, carbs * peak_ratio)
    out = np.zeros((n, 24), dtype=np.float32)
    for i, t in enumerate(LABEL_STEPS):
        if t <= 30:
            r = t / 30
            out[:, i] = current_glucose + (peak - current_glucose) * r
        elif t <= 90:
            r = (t - 30) / 60
            out[:, i] = peak - (peak - current_glucose) * r
        else:
            out[:, i] = current_glucose
    return out


def baseline_now_last_value(recent_last: np.ndarray) -> np.ndarray:
    """Model 2 baseline: 마지막 BG 값 24개 그대로 (식사 없으니 변화 없다고 가정)."""
    return baseline_last_value(recent_last)


# ─────────────────────────────────────────────────────────────────────
# Clarke Error Grid (1987 정의)
# ─────────────────────────────────────────────────────────────────────


def clarke_zone(reference: float, predicted: float) -> str:
    """단일 점의 zone (A/B/C/D/E) 판정.

    Reference: Clarke et al., Diabetes Care 1987.
    """
    r, p = float(reference), float(predicted)
    # Zone A: 둘 다 < 70, 또는 |error| <= 20% of reference
    if (r < 70 and p < 70) or (r >= 70 and abs(p - r) <= 0.2 * r):
        return "A"
    # Zone E: 정반대 진단 (저혈당↔고혈당)
    if (r >= 180 and p <= 70) or (r <= 70 and p >= 180):
        return "E"
    # Zone D: 위험한 저/고혈당 놓침
    if (r >= 240 and 70 <= p <= 180) or (r <= 70 and 70 <= p <= 180):
        return "D"
    # Zone C: 잘못된 치료 유도
    if (r >= 70 and r <= 290 and p >= r + 110) or (
        r >= 130 and r <= 180 and p <= r * 0.7 - 10
    ):
        return "C"
    # 나머지 = Zone B (임상적으로 무해한 오차)
    return "B"


def clarke_zone_counts(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, int]:
    counts = {z: 0 for z in "ABCDE"}
    for t, p in zip(y_true.flatten(), y_pred.flatten()):
        counts[clarke_zone(float(t), float(p))] += 1
    return counts


def clarke_a_plus_b_ratio(y_true: np.ndarray, y_pred: np.ndarray) -> float:
    counts = clarke_zone_counts(y_true, y_pred)
    total = sum(counts.values())
    return (counts["A"] + counts["B"]) / total if total else 0.0


# ─────────────────────────────────────────────────────────────────────
# 메트릭
# ─────────────────────────────────────────────────────────────────────


def rmse_per_horizon(y_true: np.ndarray, y_pred: np.ndarray) -> np.ndarray:
    return np.sqrt(np.mean((y_true - y_pred) ** 2, axis=0))


def mae_per_horizon(y_true: np.ndarray, y_pred: np.ndarray) -> np.ndarray:
    return np.mean(np.abs(y_true - y_pred), axis=0)


def ceg_per_horizon(y_true: np.ndarray, y_pred: np.ndarray) -> np.ndarray:
    """각 horizon 별 A+B 비율."""
    n_horizon = y_true.shape[1]
    return np.array(
        [clarke_a_plus_b_ratio(y_true[:, i], y_pred[:, i]) for i in range(n_horizon)]
    )


def peak_errors(y_true: np.ndarray, y_pred: np.ndarray) -> tuple[float, float]:
    """피크값 오차(mg/dL), 피크시점 오차(min)."""
    true_peak_val = y_true.max(axis=1)
    pred_peak_val = y_pred.max(axis=1)
    true_peak_idx = y_true.argmax(axis=1)
    pred_peak_idx = y_pred.argmax(axis=1)
    val_err = float(np.mean(np.abs(true_peak_val - pred_peak_val)))
    # idx → minutes (5분 간격)
    time_err = float(np.mean(np.abs(true_peak_idx - pred_peak_idx)) * 5)
    return val_err, time_err


def evaluate_predictions(
    y_true_raw: np.ndarray,
    y_pred_raw: np.ndarray,
    model_name: str,
    model_type: str,
) -> dict[str, Any]:
    """raw mg/dL 기준 평가 결과 dict."""
    rmse = rmse_per_horizon(y_true_raw, y_pred_raw)
    mae = mae_per_horizon(y_true_raw, y_pred_raw)
    ceg = ceg_per_horizon(y_true_raw, y_pred_raw)
    peak_val, peak_time = peak_errors(y_true_raw, y_pred_raw)
    return {
        "model_name": model_name,
        "model_type": model_type,
        "horizons_min": LABEL_STEPS,
        "rmse_per_horizon": rmse.tolist(),
        "mae_per_horizon": mae.tolist(),
        "ceg_a_plus_b_per_horizon": ceg.tolist(),
        "peak_value_error_mg_dl": peak_val,
        "peak_time_error_min": peak_time,
        "n_samples": int(y_true_raw.shape[0]),
    }


# ─────────────────────────────────────────────────────────────────────
# 시각화
# ─────────────────────────────────────────────────────────────────────


def plot_clarke_error_grid(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    horizon_min: int,
    save_path: str | Path,
    title_prefix: str = "",
) -> None:
    """1차원 y_true, y_pred 받아서 Clarke EG 산점도 + 영역 통계 박스."""
    fig, ax = plt.subplots(figsize=(7, 7))
    ax.set_facecolor("white")

    # 영역 색칠 (간단 버전 — 정확한 polygon 대신 격자로)
    _shade_zones(ax)

    # 점
    ax.scatter(y_true, y_pred, c="black", alpha=0.3, s=10)
    ax.plot([0, 400], [0, 400], "--", color="gray", linewidth=0.8)

    ax.set_xlim(0, 400)
    ax.set_ylim(0, 400)
    ax.set_xlabel("Reference glucose (mg/dL)", fontsize=12)
    ax.set_ylabel("Predicted glucose (mg/dL)", fontsize=12)
    ax.set_title(f"{title_prefix}Clarke Error Grid (PH={horizon_min}min)", fontsize=12)
    ax.grid(True, linestyle=":", color="gray", alpha=0.5)

    # 영역 통계 박스
    counts = clarke_zone_counts(y_true, y_pred)
    total = sum(counts.values())
    ratios = {z: 100 * counts[z] / total if total else 0 for z in "ABCDE"}
    text = "\n".join(f"{z}: {ratios[z]:.1f}%" for z in "ABCDE")
    text += f"\nA+B: {ratios['A'] + ratios['B']:.1f}%"
    ax.text(
        0.97,
        0.03,
        text,
        transform=ax.transAxes,
        ha="right",
        va="bottom",
        fontsize=10,
        bbox=dict(boxstyle="round,pad=0.4", facecolor="white", edgecolor="gray"),
    )

    Path(save_path).parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)


def _shade_zones(ax: plt.Axes) -> None:
    """간단한 zone 배경색 (정확한 polygon 대신 격자 샘플링).

    400×400 mg/dL 영역을 5 mg/dL 격자로 sampling 해서 색칠.
    """
    grid = np.arange(0, 405, 5)
    color_map = {
        "A": "#d4f4dd",
        "B": "#fff4cc",
        "C": "#ffcccc",
        "D": "#ffcccc",
        "E": "#ffcccc",
    }
    for r in grid[:-1]:
        for p in grid[:-1]:
            zone = clarke_zone(r + 2.5, p + 2.5)
            ax.fill_between(
                [r, r + 5],
                p,
                p + 5,
                color=color_map[zone],
                alpha=0.35,
                zorder=0,
                linewidth=0,
            )


def plot_predictions_curve(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    save_path: str | Path,
    title: str = "Predicted vs True curve",
) -> None:
    """단일 sample 의 24-step 곡선."""
    fig, ax = plt.subplots(figsize=(8, 4))
    t = np.array(LABEL_STEPS)
    ax.plot(t, y_true, "k-", label="true", linewidth=1.5)
    ax.plot(t, y_pred, "r--", label="pred", linewidth=1.5)
    ax.set_xlabel("Minutes")
    ax.set_ylabel("BG (mg/dL)")
    ax.set_title(title)
    ax.legend()
    ax.grid(True, linestyle=":", alpha=0.5)
    Path(save_path).parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)


def plot_curve_examples(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    save_path: str | Path,
    n_examples: int = 5,
    title_prefix: str = "",
) -> None:
    """N 개 sample 곡선 한 PNG에."""
    n = min(n_examples, y_true.shape[0])
    indices = np.linspace(0, y_true.shape[0] - 1, n, dtype=int)
    fig, axes = plt.subplots(n, 1, figsize=(8, 2.5 * n), squeeze=False)
    t = np.array(LABEL_STEPS)
    for ax, idx in zip(axes[:, 0], indices):
        ax.plot(t, y_true[idx], "k-", label="true")
        ax.plot(t, y_pred[idx], "r--", label="pred")
        ax.set_ylabel("BG mg/dL")
        ax.legend(loc="upper right", fontsize=8)
        ax.grid(True, linestyle=":", alpha=0.5)
    axes[-1, 0].set_xlabel("Minutes")
    axes[0, 0].set_title(f"{title_prefix}Sample predictions (n={n})")
    Path(save_path).parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)


def generate_comparison_table(
    results: list[dict[str, Any]],
    save_path: str | Path,
) -> None:
    """모델 × KEY_HORIZONS 비교 표 PNG."""
    cols = ["Model"]
    for h in KEY_HORIZONS:
        cols += [f"RMSE@{h}", f"MAE@{h}", f"CEG_A+B@{h}"]
    cols += ["Peak Val Err", "Peak Time Err", "N"]

    rows: list[list[str]] = []
    for r in results:
        row = [r["model_name"]]
        for h in KEY_HORIZONS:
            i = LABEL_STEPS.index(h)
            row += [
                f"{r['rmse_per_horizon'][i]:.1f}",
                f"{r['mae_per_horizon'][i]:.1f}",
                f"{r['ceg_a_plus_b_per_horizon'][i]*100:.1f}%",
            ]
        row += [
            f"{r['peak_value_error_mg_dl']:.1f}",
            f"{r['peak_time_error_min']:.1f}",
            str(r["n_samples"]),
        ]
        rows.append(row)

    fig, ax = plt.subplots(figsize=(2 + len(cols) * 0.9, 1 + len(rows) * 0.4))
    ax.axis("off")
    table = ax.table(cellText=rows, colLabels=cols, loc="center", cellLoc="center")
    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1, 1.4)
    Path(save_path).parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
