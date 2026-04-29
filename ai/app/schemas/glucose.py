"""혈당 예측 API 요청/응답 스키마.

엔드포인트:
- POST /api/predict/glucose/meal  (Model 1)
- POST /api/predict/glucose/now   (Model 2)
- GET  /api/predict/glucose/health
"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


# ─────────────────────────────────────────────────────────────────────
# 공통
# ─────────────────────────────────────────────────────────────────────

ActivityLiteral = Literal["low", "medium", "high"]
DiabetesTypeLiteral = Literal["T1D", "T2D", "Normal"]
MealPatternLiteral = Literal[
    "regular_3",
    "skip_breakfast",
    "skip_dinner",
    "skip_lunch",
    "skip_breakfast_dinner",
    "skip_breakfast_lunch",
    "skip_lunch_dinner",
    "frequent_small",
    "irregular",
    "late_dinner",
    "fasting_day",
]


class UserProfile(BaseModel):
    """Model 2 (현재 시점) 용 — meal_pattern 불필요."""

    fasting_bg: float = Field(description="공복 혈당 mg/dL")
    weight_kg: float = Field(gt=0, le=300)
    activity: ActivityLiteral
    diabetes_type: DiabetesTypeLiteral


class UserProfileWithPattern(UserProfile):
    """Model 1 (식사 시점) 용 — meal_pattern 추가."""

    meal_pattern: MealPatternLiteral


class MealInfo(BaseModel):
    carbs: float = Field(ge=0, le=300, description="탄수화물 g")
    time_iso: str = Field(description="식사 시각 ISO 8601 (예: 2026-04-29T08:30:00)")


class PredictResponse(BaseModel):
    """Model 1, Model 2 공통 응답.

    - horizons_min: [5, 10, ..., 120] 고정 (24개)
    - predicted: 24개 BG mg/dL (raw)
    - confidence: 임시 0.85 고정 (추후 model uncertainty 로 대체)
    - mode: "base" | "personalized" — Model 2 는 항상 "base"
    """

    horizons_min: list[int] = Field(description="예측 horizon (분), 5~120 5분 간격")
    predicted: list[float] = Field(description="예측 BG mg/dL (raw), 24개")
    confidence: float = Field(ge=0, le=1)
    mode: Literal["base", "personalized"]


# ─────────────────────────────────────────────────────────────────────
# Model 1: 식사 시점 → 식후 120분
# ─────────────────────────────────────────────────────────────────────


class MealPredictRequest(BaseModel):
    user_id: str
    recent_values: list[float] = Field(
        min_length=1,
        description="최근 BG 측정값. 가장 최근 값을 current_glucose 로 사용.",
    )
    meal: MealInfo
    user_profile: UserProfileWithPattern


# ─────────────────────────────────────────────────────────────────────
# Model 2: 현재 시점 → 향후 120분
# ─────────────────────────────────────────────────────────────────────


class NowPredictRequest(BaseModel):
    user_id: str
    recent_values: list[float] = Field(
        min_length=12,
        description="최근 60분 BG 시계열. 5분 간격 12개 (또는 그 이상, 마지막 12개 사용).",
    )
    user_profile: UserProfile


# ─────────────────────────────────────────────────────────────────────
# Health
# ─────────────────────────────────────────────────────────────────────


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED", "DOWN"]
    meal_model_loaded: bool
    now_model_loaded: bool
    scaler_loaded: bool
    cuda_available: bool
