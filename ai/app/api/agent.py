"""Agent API 라우터.

- POST /agent/morning    : 오늘의 혈당 전략 agent 실행
- POST /agent/post-meal  : 식후 활동 유도 agent 실행
- POST /trigger          : 백엔드 스케줄러가 호출하는 트리거 디스패처
"""

import asyncio
import logging
from datetime import datetime

from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool

from app.schemas.agent import (
    AgentResponse,
    MorningRequest,
    PostMealRequest,
    TriggerRequest,
)
from app.agent.morning_agent_run import run_agent
from app.agent.postmeal_agent_run import run_postmeal_agent

log = logging.getLogger(__name__)

router = APIRouter(prefix="/agent", tags=["Agent"])

# 시연용: 실제 30분 대신 이 값(초)만큼 대기 후 followup 실행
DEMO_FOLLOWUP_DELAY_SECONDS = 30


async def _run_followup_after_delay(delay_seconds: int, trigger_base: dict, user_id: str):
    """delay_seconds 후에 schedule_followup 트리거로 에이전트를 재실행한다."""
    await asyncio.sleep(delay_seconds)
    followup_trigger = {
        "reason": "schedule_followup",
        "meal_time": trigger_base.get("meal_time", ""),
        "original_reply": trigger_base.get("user_reply", ""),
        "followup_at": datetime.now().strftime("%Y-%m-%d %H:%M"),
    }
    log.info("schedule_followup 실행: user_id=%s trigger=%s", user_id, followup_trigger)
    try:
        await run_in_threadpool(
            run_postmeal_agent, followup_trigger,
            user_id=user_id, alert_type="AGENT_MEAL_RETRY",
        )
    except Exception as e:
        log.error("schedule_followup 실행 실패: %s", e)


@router.post("/morning", response_model=AgentResponse)
async def morning_agent(req: MorningRequest):
    """아침 혈당 전략 agent를 실행한다."""
    try:
        result = await run_in_threadpool(run_agent, user_id=req.user_id)
    except Exception as e:
        return AgentResponse(
            status="error",
            reasoning_trace=[],
            error=f"agent_execution_failed: {type(e).__name__}",
        )

    # 상태 판별
    if result.get("error"):
        status = "fallback" if result["error"] == "llm_call_failed" else "error"
    else:
        status = "success"

    return AgentResponse(
        status=status,
        notification_sent=result.get("message"),
        reasoning_trace=result.get("tool_call_details", []),
        error=result.get("error"),
    )


@router.post("/post-meal", response_model=AgentResponse)
async def postmeal_agent(req: PostMealRequest, background_tasks: BackgroundTasks):
    """식후 활동 유도 agent를 실행한다."""
    trigger = req.trigger.model_dump(exclude_none=True)

    try:
        result = await run_in_threadpool(
            run_postmeal_agent, trigger, user_id=req.user_id,
        )
    except Exception as e:
        return AgentResponse(
            status="error",
            reasoning_trace=[],
            error=f"agent_execution_failed: {type(e).__name__}",
        )

    if result.get("error"):
        status = "fallback" if result["error"] == "llm_call_failed" else "error"
    else:
        status = "success"

    # schedule_followup이 있으면 백그라운드에서 지연 후 재실행
    if result.get("scheduled_followup"):
        delay_min = result["scheduled_followup"].get("delay_minutes", 30)
        log.info(
            "schedule_followup 예약: %d분 후 실행 (시연 모드: %d초 후)",
            delay_min, DEMO_FOLLOWUP_DELAY_SECONDS,
        )
        background_tasks.add_task(
            _run_followup_after_delay,
            delay_seconds=DEMO_FOLLOWUP_DELAY_SECONDS,
            trigger_base=trigger,
            user_id=req.user_id,
        )

    return AgentResponse(
        status=status,
        notification_sent=result.get("message"),
        reasoning_trace=result.get("tool_call_details", []),
        scheduled_followup=result.get("scheduled_followup"),
        error=result.get("error"),
    )


@router.post("/trigger", response_model=AgentResponse)
async def dispatch_trigger(req: TriggerRequest):
    """백엔드 AgentTriggerScheduler가 호출하는 트리거 디스패처.
    triggerType에 따라 적절한 agent를 실행한다.
    """
    log.info("trigger received: type=%s userId=%s ref=%s", req.triggerType, req.userId, req.referenceId)

    if req.triggerType == "post_meal":
        trigger = {
            "reason": "meal_recorded",
            "meal_time": "",  # referenceId로 조회 가능하나 현재 mock에서는 빈값 허용
        }
        try:
            result = await run_in_threadpool(
                run_postmeal_agent, trigger, user_id=str(req.userId),
            )
        except Exception as e:
            return AgentResponse(
                status="error",
                reasoning_trace=[],
                error=f"agent_execution_failed: {type(e).__name__}",
            )

    elif req.triggerType == "post_meal_followup":
        followup_trigger = {
            "reason": "schedule_followup",
            "meal_time": "",
            "original_reply": "",
            "followup_at": datetime.now().strftime("%Y-%m-%d %H:%M"),
        }
        try:
            result = await run_in_threadpool(
                run_postmeal_agent, followup_trigger,
                user_id=str(req.userId), alert_type="AGENT_MEAL_RETRY",
            )
        except Exception as e:
            return AgentResponse(
                status="error",
                reasoning_trace=[],
                error=f"agent_execution_failed: {type(e).__name__}",
            )

    elif req.triggerType == "morning":
        try:
            result = await run_in_threadpool(run_agent, user_id=str(req.userId))
        except Exception as e:
            return AgentResponse(
                status="error",
                reasoning_trace=[],
                error=f"agent_execution_failed: {type(e).__name__}",
            )

    else:
        log.warning("unknown triggerType: %s", req.triggerType)
        return AgentResponse(
            status="error",
            reasoning_trace=[],
            error=f"unknown_trigger_type: {req.triggerType}",
        )

    if result.get("error"):
        status = "fallback" if result["error"] == "llm_call_failed" else "error"
    else:
        status = "success"

    return AgentResponse(
        status=status,
        notification_sent=result.get("message"),
        reasoning_trace=result.get("tool_call_details", []),
        scheduled_followup=result.get("scheduled_followup"),
        error=result.get("error"),
    )
