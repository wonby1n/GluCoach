"""Agent API 라우터.

- POST /agent/morning    : 오늘의 혈당 전략 agent 실행
- POST /agent/post-meal  : 식후 활동 유도 agent 실행
- POST /trigger          : 백엔드 스케줄러가 호출하는 트리거 디스패처
"""

import logging

from fastapi import APIRouter
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
async def postmeal_agent(req: PostMealRequest):
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
