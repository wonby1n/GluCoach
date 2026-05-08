"""
4개 스칼라 → 24-point 혈당 곡선 재구성.

skewed Gaussian 사용:
  - 상승 구간: Gaussian 좌측 (빠른 상승)
  - 하강 구간: Gaussian 우측을 decay_rate로 늘린 형태 (느린 회복)
"""

from __future__ import annotations

import numpy as np

LABEL_STEPS = list(range(5, 125, 5))  # [5, 10, ..., 120] — 24개


def build_curve(
    peak_delta: float,
    time_to_peak: float,
    decay_rate: float,
    baseline_glucose: float,
) -> list[float]:
    """
    4개 스칼라 + 기저 혈당 → 24-point 혈당 곡선 (5분 간격, 2시간).

    Args:
        peak_delta:      기저 대비 최대 상승량 (mg/dL)
        time_to_peak:    peak 도달 시간 (분, 5~120)
        decay_rate:      회복 속도 (값이 클수록 빠른 회복). 훈련 시 단위: XGBoost 출력
        baseline_glucose: 식사 직전 CGM 값 (mg/dL)

    Returns:
        24개 혈당값 리스트 (mg/dL)
    """
    t = np.array(LABEL_STEPS, dtype=float)
    ttp = float(np.clip(time_to_peak, 5.0, 120.0))
    pd_val = max(0.0, peak_delta)

    # 상승 구간 sigma: peak까지 도달 시간의 1/2.5
    sigma_rise = max(ttp / 2.5, 3.0)
    # 하강 구간 sigma: decay_rate가 클수록 좁은 sigma (빠른 회복)
    # decay_rate 범위 대략 0.1~10 → sigma_fall 30~120분
    sigma_fall = max(120.0 / (decay_rate + 1.0), 15.0)

    delta = np.where(
        t <= ttp,
        pd_val * np.exp(-0.5 * ((t - ttp) / sigma_rise) ** 2),
        pd_val * np.exp(-0.5 * ((t - ttp) / sigma_fall) ** 2),
    )

    curve = baseline_glucose + delta
    # 혈당은 20 mg/dL 이하로 떨어지지 않도록 클리핑
    curve = np.clip(curve, 20.0, 600.0)
    return [round(float(v), 2) for v in curve]


def scalars_to_response(
    peak_delta: float,
    time_to_peak: float,
    decay_rate: float,
    baseline_glucose: float,
) -> dict:
    """
    build_curve 결과에서 peak/iAUC 메타도 함께 반환.

    Returns:
        {curve, peak_mgdl, peak_minute, iauc_2h}
    """
    curve = build_curve(peak_delta, time_to_peak, decay_rate, baseline_glucose)
    arr = np.array(curve)
    peak_idx = int(np.argmax(arr))

    delta_arr = np.clip(arr - baseline_glucose, 0.0, None)
    iauc = float(np.trapezoid(delta_arr) * 5)

    return {
        "curve": [
            {"t_min": t, "glucose": v}
            for t, v in zip(LABEL_STEPS, curve)
        ],
        "peak_mgdl": round(float(arr[peak_idx]), 2),
        "peak_minute": LABEL_STEPS[peak_idx],
        "iauc_2h": round(iauc, 1),
    }
