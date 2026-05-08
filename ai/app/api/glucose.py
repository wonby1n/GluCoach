"""혈당 예측 FastAPI 라우터 (Jira S14P31S309-278).

prefix: /inference/glucose
- POST /meal         — Model 1, 음식 선택 시 식후 120분 예측
- POST /now          — Model 2, 현재 시점 향후 120분 예측 (식사 없음 가정)
- POST /personalize  — 환자별 fine-tune (개선 없으면 자동 폐기)
- GET  /health       — 모델 로드 상태
"""

from __future__ import annotations

import asyncio
import logging
from pathlib import Path

from fastapi import APIRouter, HTTPException

from app.glucose import config, interface, personalize
from app.glucose.predict import reset_predictors
from app.schemas.glucose import (
    HealthResponse,
    MealPredictRequest,
    NowPredictRequest,
    PersonalizeRequest,
    PersonalizeResponse,
    PredictResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/inference/glucose", tags=["glucose"])


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


@router.post("/personalize", response_model=PersonalizeResponse)
async def personalize_user(req: PersonalizeRequest) -> PersonalizeResponse:
    """환자별 fine-tune. base 보다 개선 없으면 자동 폐기 (rejected)."""
    base_path = Path(config.MODELS_DIR) / "lstm_meal.pt"
    if not base_path.exists():
        raise HTTPException(
            status_code=503,
            detail="base 모델 (lstm_meal.pt) 미존재. 학습 필요.",
        )
    save_path = Path(config.MODELS_DIR) / f"lstm_meal_personalized_{req.user_id}.pt"

    personalize.cleanup_old_personalized_models()

    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(
            None,
            lambda: personalize.finetune_from_history(
                user_id=req.user_id,
                history=[item.model_dump() for item in req.history],
                user_profile=req.user_profile.model_dump(),
                base_model_path=base_path,
                save_path=save_path,
            ),
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception:
        logger.exception("personalize failed")
        raise HTTPException(status_code=500, detail="internal error")

    # 해당 사용자 캐시만 초기화 (다른 사용자 캐시 유지)
    reset_predictors(user_id=req.user_id)

    if result["status"] == "rejected":
        message = (
            f"개선 없음 (base RMSE@30={result['base_rmse_30min']:.2f}, "
            f"personalized={result['personalized_rmse_30min']:.2f}). "
            f"base 그대로 사용."
        )
    else:
        message = (
            f"{result['improvement_percent']:.1f}% 개선 "
            f"(base RMSE@30={result['base_rmse_30min']:.2f} → "
            f"personalized={result['personalized_rmse_30min']:.2f})"
        )

    return PersonalizeResponse(
        user_id=req.user_id,
        status=result["status"],
        n_samples=result["n_samples"],
        base_rmse_30min=result["base_rmse_30min"],
        personalized_rmse_30min=result["personalized_rmse_30min"],
        improvement_percent=result["improvement_percent"],
        message=message,
    )


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """모델/scaler 로드 상태 + GPU 가용성."""
    return interface.health_check()
