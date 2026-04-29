"""혈당 예측 FastAPI 라우터 (Jira S14P31S309-278).

prefix: /api/predict/glucose
- POST /meal   — Model 1, 음식 선택 시 식후 120분 예측
- POST /now    — Model 2, 현재 시점 향후 120분 예측 (식사 없음 가정)
- GET  /health — 모델 로드 상태
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.glucose import interface
from app.schemas.glucose import (
    HealthResponse,
    MealPredictRequest,
    NowPredictRequest,
    PredictResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/predict/glucose", tags=["glucose"])


@router.post("/meal", response_model=PredictResponse)
async def predict_meal(req: MealPredictRequest) -> PredictResponse:
    """식사 시점 → 식후 120분 BG 곡선 예측."""
    try:
        return interface.predict_meal_response(req.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception:
        logger.exception("meal prediction failed")
        raise HTTPException(status_code=500, detail="internal error")


@router.post("/now", response_model=PredictResponse)
async def predict_now(req: NowPredictRequest) -> PredictResponse:
    """현재 시점 → 향후 120분 BG 곡선 예측 (식사 없음 가정)."""
    try:
        return interface.predict_now(req.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception:
        logger.exception("now prediction failed")
        raise HTTPException(status_code=500, detail="internal error")


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """모델/scaler 로드 상태 + GPU 가용성."""
    return interface.health_check()
