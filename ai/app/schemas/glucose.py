"""혈당 예측 API 요청/응답 스키마.

엔드포인트:
- POST /inference/glucose/meal  (Model 1)
- POST /inference/glucose/now   (Model 2)
- GET  /inference/glucose/health
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
    """Model 2 (현재 시점) 용 — meal_pattern 불필요.

    fasting_bg / activity 는 백엔드가 항상 제공할 수 없으므로 optional.
    - fasting_bg: CGM 기록에서 오늘 아침 최저값으로 채워지지만, 없으면 100.0 사용.
    - activity: 삼성헬스 SDK 연동 전까지 앱에서 전달 불가 → "medium" 기본값.
    """

    fasting_bg: float = Field(default=100.0, description="공복 혈당 mg/dL. 없으면 100.0 사용.")
    weight_kg: float = Field(gt=0, le=300)
    activity: ActivityLiteral = "medium"
    diabetes_type: DiabetesTypeLiteral


class UserProfileWithPattern(UserProfile):
    """Model 1 (식사 시점) 용 — meal_pattern 추가.

    meal_pattern: User 테이블에 온보딩 시 저장되는 식사 습관. 없으면 "regular_3" 기본값.
    """

    meal_pattern: MealPatternLiteral = "regular_3"


class MealInfo(BaseModel):
    carbs: float = Field(ge=0, le=300, description="탄수화물 g")
    time_iso: str = Field(description="식사 시각 ISO 8601 (예: 2026-04-29T08:30:00)")


class GlucosePoint(BaseModel):
    """단일 시점 예측값 — curve 배열의 원소."""

    minute_offset: int = Field(description="현재 시점 기준 경과 분 (5, 10, ..., 120)")
    glucose_mgdl: float = Field(description="예측 BG mg/dL")


class PredictResponse(BaseModel):
    """Model 1, Model 2 공통 응답.

    - curve: 5~120분 24개 시점 예측 BG 곡선
    - peak_mgdl / peak_minute: 예측 곡선 내 피크값 및 발생 시점
    - model_type: "base" | "personalized" — Model 2 는 항상 "base"
    - confidence: 임시 0.85 고정 (추후 model uncertainty 로 대체)

    JSON 키 이름은 snake_case 사용.
    백엔드(Spring Boot) Jackson 설정: PropertyNamingStrategies.SNAKE_CASE 적용 필요.
    """

    curve: list[GlucosePoint] = Field(description="예측 BG 곡선, 24개 시점")
    peak_mgdl: float = Field(description="예측 피크 BG mg/dL")
    peak_minute: int = Field(description="피크 발생 시점 (분)")
    model_type: str = Field(description="base | personalized")
    confidence: float = Field(ge=0, le=1)


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
# 개인화 fine-tune
# ─────────────────────────────────────────────────────────────────────


class MealHistoryItem(BaseModel):
    """환자 본인의 식사 + 식후 24개 실측 BG 페어 (fine-tune 학습 데이터)."""

    carbs: float = Field(ge=0, le=300)
    meal_time_iso: str
    current_glucose: float = Field(ge=20, le=600)
    bg_curve: list[float] = Field(
        min_length=24,
        max_length=24,
        description="식후 5/10/15/.../120분 BG 24개 (mg/dL, raw)",
    )


class PersonalizeRequest(BaseModel):
    user_id: str = Field(min_length=1)
    user_profile: UserProfileWithPattern
    history: list[MealHistoryItem] = Field(
        min_length=10,
        description="환자 본인 식사+실측 페어. 최소 10개. 권장 30+",
    )


class PersonalizeResponse(BaseModel):
    user_id: str
    status: Literal["personalized", "rejected"]
    n_samples: int
    base_rmse_30min: float
    personalized_rmse_30min: float
    improvement_percent: float = Field(description="음수면 base 가 더 좋음 (rejected)")
    message: str


# ─────────────────────────────────────────────────────────────────────
# Health
# ─────────────────────────────────────────────────────────────────────


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED", "DOWN"]
    meal_model_loaded: bool
    now_model_loaded: bool
    scaler_loaded: bool
    cuda_available: bool
