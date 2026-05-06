"""Agent API 요청/응답 스키마.

엔드포인트:
- POST /agent/morning
- POST /agent/post-meal
- POST /trigger
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
    meal_time: str = Field(..., description="식사 시각 (예: 2026-05-04 12:00)")

    # user_response일 때
    user_reply: Optional[str] = Field(None, description="사용자 응답 텍스트")
    previous_notification_sent_at: Optional[str] = Field(None, description="이전 알림 발송 시각")

    # schedule_followup일 때
    original_reply: Optional[str] = Field(None, description="원래 사용자 응답")
    followup_at: Optional[str] = Field(None, description="재시도 예약 시각")


class PostMealRequest(BaseModel):
    user_id: str = Field(..., description="사용자 ID")
    trigger: PostMealTrigger


# ── 응답 ─────────────────────────────────────────────────

class ToolCallDetail(BaseModel):
    name: str
    input: dict
    result: dict


class AgentResponse(BaseModel):
    status: Literal["success", "fallback", "error"] = Field(
        ..., description="실행 결과 상태"
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
