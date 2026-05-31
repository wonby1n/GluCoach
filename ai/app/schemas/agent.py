"""Agent API 요청/응답 스키마.

엔드포인트:
- POST /agent/morning
- POST /agent/post-meal
- POST /trigger
- POST /agent/food-compare
"""

from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field


# ── 요청 ─────────────────────────────────────────────────

class TriggerRequest(BaseModel):
    """백엔드 AgentTriggerDispatcher가 POST /trigger로 전송하는 페이로드."""
    userId: int = Field(..., description="사용자 ID")
    triggerType: str = Field(..., description="트리거 유형 (post_meal | morning)")
    referenceId: Optional[int] = Field(None, description="참조 ID (식사 기록 ID 등)")


class MorningRequest(BaseModel):
    user_id: str = Field(..., description="사용자 ID")


class PostMealTrigger(BaseModel):
    reason: Literal["meal_recorded", "user_response", "schedule_followup"] = Field(
        ..., description="트리거 이유"
    )
    meal_time: str = Field("", description="식사 시각 (예: 2026-05-04 12:00)")

    # user_response일 때
    user_reply: Optional[str] = Field(None, description="사용자 응답 텍스트")
    previous_notification_sent_at: Optional[str] = Field(None, description="이전 알림 발송 시각")

    # schedule_followup일 때
    original_reply: Optional[str] = Field(None, description="원래 사용자 응답")
    followup_at: Optional[str] = Field(None, description="재시도 예약 시각")


class PostMealRequest(BaseModel):
    user_id: str = Field(..., description="사용자 ID")
    trigger: PostMealTrigger


class FoodSummary(BaseModel):
    name: str = Field(..., description="음식 이름")
    peak_mgdl: float = Field(..., description="예측 최고 혈당 (mg/dL)")
    peak_minute: int = Field(..., description="최고 혈당 도달 시간 (분)")
    slope: float = Field(..., description="분당 혈당 상승 속도 (mg/dL/min)")


class FoodCompareUserProfile(BaseModel):
    diabetes_type: str = Field("Normal", description="당뇨 유형 (Normal|T1D|T2D)")
    target_low: Optional[float] = Field(None, description="혈당 목표 하한 (mg/dL)")
    target_high: Optional[float] = Field(None, description="혈당 목표 상한 (mg/dL)")


class FoodCompareRequest(BaseModel):
    user_id: str = Field(..., description="사용자 ID")
    user_name: Optional[str] = Field(None, description="사용자 이름 (개인화용)")
    food_a: FoodSummary
    food_b: FoodSummary
    user_profile: FoodCompareUserProfile


class FoodCompareResponse(BaseModel):
    message: str = Field(..., description="개인화된 비교 설명 문구")
    status: Literal["success", "fallback"]


# ── 응답 ─────────────────────────────────────────────────

class ToolCallDetail(BaseModel):
    name: str
    input: dict
    result: dict


class AgentResponse(BaseModel):
    status: Literal["success", "fallback", "error", "accepted"] = Field(
        ..., description="실행 결과 상태 (accepted: 백그라운드 처리 중)"
    )
    notification_sent: Optional[str] = Field(
        None, description="발송된 알림 메시지"
    )
    reasoning_trace: list[ToolCallDetail] = Field(
        default_factory=list, description="도구 호출 이력"
    )
    scheduled_followup: Optional[dict] = Field(
        None, description="재시도 예약 정보 (식후 agent 전용)"
    )
    error: Optional[str] = Field(None, description="에러 정보")
