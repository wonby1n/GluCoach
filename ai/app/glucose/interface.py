"""api/glucose.py 가 호출하는 추론 인터페이스.

predict.py 의 실제 모델 추론을 시도하고, 모델/scaler 미존재 시 **dummy 응답** 폴백.
→ 학습 전 단계에선 백엔드 통합 테스트 가능, 학습 후엔 자동 실제 추론.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import torch

from app.glucose import config, predict
from app.glucose.constants import LABEL_STEPS
from app.schemas.glucose import GlucosePoint, HealthResponse, PredictResponse

logger = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────────
# Dummy fallback (학습 전 단계 또는 추론 실패 시)
# ─────────────────────────────────────────────────────────────────────


def _dummy_curve_from_meal(carbs: float, current_glucose: float) -> list[float]:
    """식후 30분 피크 휴리스틱."""
    peak = current_glucose + max(0.0, carbs * 0.6)
    values: list[float] = []
    for t in LABEL_STEPS:
        if t <= 30:
            ratio = t / 30
            v = current_glucose + (peak - current_glucose) * ratio
        elif t <= 90:
            ratio = (t - 30) / 60
            v = peak - (peak - current_glucose) * ratio
        else:
            v = current_glucose
        values.append(round(v, 2))
    return values


def _dummy_curve_from_recent(recent_values: list[float]) -> list[float]:
    base = recent_values[-1] if recent_values else 100.0
    return [round(base + (i % 5 - 2) * 0.5, 2) for i in range(len(LABEL_STEPS))]


# ─────────────────────────────────────────────────────────────────────
# 응답 조립 헬퍼
# ─────────────────────────────────────────────────────────────────────


def _build_predict_response(
    horizons: list[int],
    predicted: list[float],
    baseline: float,
    model_type: str,
    confidence: float,
) -> PredictResponse:
    """curve 배열과 파생 지표(peak/return)를 계산해 PredictResponse 를 만든다.

    return_minute: 피크 이후 처음으로 상승분의 80% 이상이 복귀되는 시점.
    피크 이전이거나 상승이 없으면 마지막 시점 반환.
    """
    curve = [
        GlucosePoint(minute_offset=h, glucose_mgdl=v)
        for h, v in zip(horizons, predicted)
    ]

    peak_mgdl = max(predicted) if predicted else baseline
    peak_idx = predicted.index(peak_mgdl) if predicted else 0
    peak_minute = horizons[peak_idx] if predicted else horizons[-1]

    rise = peak_mgdl - baseline
    return_threshold = baseline + rise * 0.2  # 80% 복귀 = 상승분의 20%만 남은 시점
    return_minute = horizons[-1]
    for i in range(peak_idx, len(predicted)):
        if predicted[i] <= return_threshold:
            return_minute = horizons[i]
            break

    return PredictResponse(
        curve=curve,
        peak_mgdl=round(peak_mgdl, 2),
        peak_minute=peak_minute,
        return_minute=return_minute,
        model_type=model_type,
        confidence=confidence,
    )


# ─────────────────────────────────────────────────────────────────────
# Public API (api/glucose.py 호출)
# ─────────────────────────────────────────────────────────────────────


def predict_meal_response(request: dict[str, Any]) -> PredictResponse:
    """Model 1: 식사 시점 → 식후 120분 BG.

    user_id 에 개인화 모델이 있으면 자동 사용, 없으면 베이스 모델.
    실제 모델 시도 → 실패 시 dummy 폴백.
    recent_values 없으면 400 (ValueError).
    """
    recent: list[float] = request.get("recent_values") or []
    if not recent:
        raise ValueError("recent_values is required (>=1)")

    baseline = float(recent[-1])
    user_id: str | None = request.get("user_id")
    predicted: list[float]
    model_type = "base"
    used_dummy = False
    try:
        predictor = predict.get_meal_predictor(model_type=config.MEAL_MODEL_TYPE, user_id=user_id)
        predicted = predictor.predict(request)
        model_type = predictor.mode
    except (RuntimeError, FileNotFoundError) as e:
        logger.warning(f"meal model not loaded → dummy fallback: {e}")
        used_dummy = True
        carbs = float(request["meal"]["carbs"])
        predicted = _dummy_curve_from_meal(carbs, baseline)

    return _build_predict_response(
        horizons=LABEL_STEPS,
        predicted=predicted,
        baseline=baseline,
        model_type=model_type,
        confidence=0.85 if not used_dummy else 0.3,
    )


def predict_now(request: dict[str, Any]) -> PredictResponse:
    """Model 2: 현재 시점 → 향후 120분 BG."""
    recent: list[float] = request.get("recent_values") or []
    if len(recent) < 12:
        raise ValueError(f"recent_values must have >=12 items, got {len(recent)}")

    baseline = float(recent[-1])
    predicted: list[float]
    used_dummy = False
    try:
        predictor = predict.get_now_predictor()
        predicted = predictor.predict(request)
    except (RuntimeError, FileNotFoundError) as e:
        logger.warning(f"now model not loaded → dummy fallback: {e}")
        used_dummy = True
        predicted = _dummy_curve_from_recent(recent)

    return _build_predict_response(
        horizons=LABEL_STEPS,
        predicted=predicted,
        baseline=baseline,
        model_type="base",
        confidence=0.85 if not used_dummy else 0.3,
    )


def health_check() -> HealthResponse:
    """모델/scaler 파일 존재 여부 + GPU 가용성."""
    models_dir = Path(config.MODELS_DIR)
    meal_loaded = any(
        (models_dir / name).exists()
        for name in ("lstm_meal.pt", "mlp_meal.pt", "ridge_meal.pkl")
    )
    now_loaded = (models_dir / "lstm_now.pt").exists()
    scaler_loaded = Path(config.SCALER_PATH).exists()

    if meal_loaded and now_loaded and scaler_loaded:
        status = "UP"
    elif meal_loaded or now_loaded or scaler_loaded:
        status = "DEGRADED"
    else:
        status = "DOWN"

    return HealthResponse(
        status=status,
        meal_model_loaded=meal_loaded,
        now_model_loaded=now_loaded,
        scaler_loaded=scaler_loaded,
        cuda_available=torch.cuda.is_available(),
    )
