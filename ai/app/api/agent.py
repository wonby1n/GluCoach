"""Agent API 라우터.

- POST /agent/morning    : 오늘의 혈당 전략 agent 실행
- POST /agent/post-meal  : 식후 활동 유도 agent 실행
- POST /trigger          : 백엔드 스케줄러가 호출하는 트리거 디스패처
"""

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


# ── alert_type 매핑 ─────────────────────────────────────────
_ALERT_TYPE_MAP = {
    "meal_recorded": "AGENT_MEAL_FOLLOWUP",
    "user_response": "AGENT_MEAL_REPLY",
    "schedule_followup": "AGENT_MEAL_RETRY",
}


async def _run_postmeal_background(trigger: dict, user_id: str):
    """백그라운드에서 postmeal agent를 실행한다.

    NOTE: followup 재실행은 schedule_followup 도구가 백엔드 API를 호출해
    백엔드 스케줄러가 처리하므로, 여기서는 agent만 실행하면 된다.
    """
    reason = trigger.get("reason", "meal_recorded")
    alert_type = _ALERT_TYPE_MAP.get(reason, "AGENT_MEAL_FOLLOWUP")

    try:
        result = await run_in_threadpool(
            run_postmeal_agent, trigger,
            user_id=user_id, alert_type=alert_type,
        )
    except Exception as e:
        log.error("background postmeal agent failed: %s", e)
        return

    log.info(
        "background postmeal agent done: reason=%s message=%s scheduled=%s",
        reason, result.get("message"), result.get("scheduled_followup"),
    )


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
    reason = trigger.get("reason", "meal_recorded")

    # user_response / schedule_followup → 즉시 응답, 백그라운드 처리
    if reason in ("user_response", "schedule_followup"):
        log.info("async dispatch: reason=%s user_id=%s", reason, req.user_id)
        background_tasks.add_task(
            _run_postmeal_background, trigger, str(req.user_id),
        )
        return AgentResponse(
            status="accepted",
            reasoning_trace=[],
        )

    # meal_recorded → 동기 처리 (알림 + 버튼 응답이 필요하므로)
    alert_type = _ALERT_TYPE_MAP.get(reason, "AGENT_MEAL_FOLLOWUP")
    try:
        result = await run_in_threadpool(
            run_postmeal_agent, trigger,
            user_id=req.user_id, alert_type=alert_type,
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

    return AgentResponse(
        status=status,
        notification_sent=result.get("message"),
        reasoning_trace=result.get("tool_call_details", []),
        scheduled_followup=result.get("scheduled_followup"),
        error=result.get("error"),
    )


@router.post("/trigger", response_model=AgentResponse)
async def dispatch_trigger(req: TriggerRequest, background_tasks: BackgroundTasks):
    """백엔드 AgentTriggerScheduler가 호출하는 트리거 디스패처.
    triggerType에 따라 적절한 agent를 실행한다.
    """
    log.info("trigger received: type=%s userId=%s ref=%s", req.triggerType, req.userId, req.referenceId)

    # post_meal_followup → 즉시 응답, 백그라운드 처리
    if req.triggerType == "post_meal_followup":
        followup_trigger = {
            "reason": "schedule_followup",
            "meal_time": "",
            "original_reply": "",
            "followup_at": datetime.now().strftime("%Y-%m-%d %H:%M"),
        }
        log.info("async dispatch trigger: post_meal_followup user_id=%s", req.userId)
        background_tasks.add_task(
            _run_postmeal_background, followup_trigger, str(req.userId),
        )
        return AgentResponse(
            status="accepted",
            reasoning_trace=[],
        )

    if req.triggerType == "post_meal":
        trigger = {
            "reason": "meal_recorded",
            "meal_time": "",  # referenceId로 조회 가능하나 현재 mock에서는 빈값 허용
        }
        alert_type = "AGENT_MEAL_FOLLOWUP"
        try:
            result = await run_in_threadpool(
                run_postmeal_agent, trigger,
                user_id=str(req.userId), alert_type=alert_type,
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
