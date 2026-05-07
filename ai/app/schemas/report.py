"""주간 보고서 API 요청/응답 스키마."""

from __future__ import annotations

from typing import Optional
from pydantic import BaseModel, Field, model_validator


class HourlyGlucose(BaseModel):
    hour: int = Field(..., ge=0, le=23)
    avg: float


class DailyGlucose(BaseModel):
    date: str  # "2025-01-01"
    avg: float
    min: float
    max: float


class FoodItem(BaseModel):
    food_name: str
    avg_slope: float


class WeeklyReportRequest(BaseModel):
    user_id: int
    week_start: str   # "2025-01-01"
    week_end: str     # "2025-01-07"

    # 사용자 정보
    user_name: str
    diabetes_type: str        # "1", "2", "NORMAL"
    target_low: float = 70.0
    target_high: float = 180.0

    # 혈당 통계 (BE에서 glucose_records 집계)
    avg_glucose: float = Field(..., gt=0)
    min_glucose: float
    max_glucose: float
    glucose_sd: float
    time_in_range: float    # TIR %
    time_above_range: float  # TAR %
    time_below_range: float  # TBR %

    # 시간대별 패턴 (0~23시 평균, BE에서 GROUP BY HOUR 집계)
    hourly_avg_glucose: list[HourlyGlucose] = Field(default_factory=list)

    # 일별 혈당 추이 (최대 7일, PDF 차트용)
    daily_avg_glucose: list[DailyGlucose] = Field(default_factory=list)

    # 식사 데이터 (meal_glucose_responses + user_food_grades 기반)
    good_foods: list[FoodItem] = Field(default_factory=list)  # 이번 주 혈당 반응 좋은 음식
    bad_foods: list[FoodItem] = Field(default_factory=list)   # 이번 주 혈당 반응 나쁜 음식
    meal_count: int = 0

    # 활동 (daily_health_summaries)
    weekly_avg_steps: Optional[float] = None
    weekly_total_calories: Optional[float] = None

    # 수면 (daily_health_summaries.sleep_minutes)
    weekly_avg_sleep_minutes: Optional[float] = None

    # 복약 (medications_records 주간 COUNT)
    medication_count: Optional[int] = None

    @model_validator(mode="after")
    def check_tir_sum(self) -> "WeeklyReportRequest":
        total = self.time_in_range + self.time_above_range + self.time_below_range
        if not (95.0 <= total <= 105.0):
            raise ValueError(f"TIR 합계가 100%에서 벗어남: {total:.1f}%")
        return self


class WeeklyReportResponse(BaseModel):
    status: str  # "success" | "error"
    ai_summary: Optional[str] = None
    ai_suggest: Optional[str] = None
    pdf_key: Optional[str] = None
    error: Optional[str] = None
