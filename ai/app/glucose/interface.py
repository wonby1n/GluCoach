"""api/glucose.py 가 호출하는 추론 인터페이스.

predict.py 의 실제 모델 추론을 시도하고, 모델/scaler 미존재 시 **dummy 응답** 폴백.
→ 학습 전 단계에선 백엔드 통합 테스트 가능, 학습 후엔 자동 실제 추론.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

import torch

from app.glucose import predict
from app.glucose.constants import DEFAULT_MODELS_DIR, DEFAULT_SCALER_PATH, LABEL_STEPS
from app.schemas.glucose import HealthResponse, PredictResponse

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
# Public API (api/glucose.py 호출)
# ─────────────────────────────────────────────────────────────────────


def predict_meal_response(request: dict[str, Any]) -> PredictResponse:
    """Model 1: 식사 시점 → 식후 120분 BG.

    실제 모델 시도 → 실패 시 dummy 폴백.
    """
    recent: list[float] = request.get("recent_values") or []
    if not recent:
        raise ValueError("recent_values is required (>=1)")

    predicted: list[float]
    used_dummy = False
    try:
        predictor = predict.get_meal_predictor(model_type="lstm")
        predicted = predictor.predict(request)
    except (RuntimeError, FileNotFoundError) as e:
        logger.warning(f"meal model not loaded → dummy fallback: {e}")
        used_dummy = True
        carbs = float(request["meal"]["carbs"])
        current_glucose = float(recent[-1])
        predicted = _dummy_curve_from_meal(carbs, current_glucose)

    mode = "personalized" if _has_personalized_model(request.get("user_id")) else "base"
    return PredictResponse(
        horizons_min=LABEL_STEPS,
        predicted=predicted,
        confidence=0.85 if not used_dummy else 0.3,
        mode=mode,
    )


def predict_now(request: dict[str, Any]) -> PredictResponse:
    """Model 2: 현재 시점 → 향후 120분 BG."""
    recent: list[float] = request.get("recent_values") or []
    if len(recent) < 12:
        raise ValueError(f"recent_values must have >=12 items, got {len(recent)}")

    predicted: list[float]
    used_dummy = False
    try:
        predictor = predict.get_now_predictor()
        predicted = predictor.predict(request)
    except (RuntimeError, FileNotFoundError) as e:
        logger.warning(f"now model not loaded → dummy fallback: {e}")
        used_dummy = True
        predicted = _dummy_curve_from_recent(recent)

    return PredictResponse(
        horizons_min=LABEL_STEPS,
        predicted=predicted,
        confidence=0.85 if not used_dummy else 0.3,
        mode="base",
    )


def health_check() -> HealthResponse:
    """모델/scaler 파일 존재 여부 + GPU 가용성."""
    models_dir = Path(DEFAULT_MODELS_DIR)
    meal_loaded = any(
        (models_dir / name).exists()
        for name in ("lstm_meal.pt", "mlp_meal.pt", "ridge_meal.pkl")
    )
    now_loaded = (models_dir / "lstm_now.pt").exists()
    scaler_loaded = Path(DEFAULT_SCALER_PATH).exists()

    if meal_loaded and now_loaded and scaler_loaded:
        status = "UP"
    elif scaler_loaded and (meal_loaded or now_loaded):
        status = "DEGRADED"
    else:
        status = "DEGRADED"

    return HealthResponse(
        status=status,
        meal_model_loaded=meal_loaded,
        now_model_loaded=now_loaded,
        scaler_loaded=scaler_loaded,
        cuda_available=torch.cuda.is_available(),
    )


# ─────────────────────────────────────────────────────────────────────
# 내부
# ─────────────────────────────────────────────────────────────────────


def _has_personalized_model(user_id: str | None) -> bool:
    if not user_id:
        return False
    path = Path(DEFAULT_MODELS_DIR) / f"lstm_meal_personalized_{user_id}.pt"
    return path.exists()
