"""Agent API 라우터.

- POST /agent/morning    : 오늘의 혈당 전략 agent 실행
- POST /agent/post-meal  : 식후 활동 유도 agent 실행
- POST /trigger          : 백엔드 스케줄러가 호출하는 트리거 디스패처
- POST /agent/food-compare : 두 음식 혈당 비교 개인화 설명 (동기)
"""

import logging
import os
from datetime import datetime

import anthropic
from fastapi import APIRouter, BackgroundTasks
from fastapi.concurrency import run_in_threadpool

from app.schemas.agent import (
    AgentResponse,
    FoodCompareRequest,
    FoodCompareResponse,
    MorningRequest,
    PostMealRequest,
    TriggerRequest,
)
from app.agent.morning_agent_run import run_agent
from app.agent.postmeal_agent_run import run_postmeal_agent
from app.agent.fallback import call_llm_with_retry

log = logging.getLogger(__name__)

_FOOD_COMPARE_MODEL = "claude-haiku-4-5-20251001"
_FOOD_COMPARE_MAX_TOKENS = 300

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


async def _run_morning_background(user_id: str):
    """백그라운드에서 morning agent를 실행한다."""
    try:
        result = await run_in_threadpool(run_agent, user_id=user_id)
    except Exception as e:
        log.error("background morning agent failed: %s", e)
        return

    log.info(
        "background morning agent done: message=%s",
        result.get("message"),
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

    # post_meal → 즉시 응답, 백그라운드 처리 (동기 시 타임아웃 → 무한 재시도 방지)
    if req.triggerType == "post_meal":
        trigger = {
            "reason": "meal_recorded",
            "meal_time": "",
        }
        log.info("async dispatch trigger: post_meal user_id=%s", req.userId)
        background_tasks.add_task(
            _run_postmeal_background, trigger, str(req.userId),
        )
        return AgentResponse(
            status="accepted",
            reasoning_trace=[],
        )

    # morning → 즉시 응답, 백그라운드 처리
    if req.triggerType == "morning":
        log.info("async dispatch trigger: morning user_id=%s", req.userId)
        background_tasks.add_task(
            _run_morning_background, str(req.userId),
        )
        return AgentResponse(
            status="accepted",
            reasoning_trace=[],
        )

    log.warning("unknown triggerType: %s", req.triggerType)
    return AgentResponse(
        status="error",
        reasoning_trace=[],
        error=f"unknown_trigger_type: {req.triggerType}",
    )


def _build_food_compare_prompt(req: FoodCompareRequest) -> str:
    name_prefix = f"{req.user_name}님" if req.user_name else "사용자"
    profile = req.user_profile
    diabetes_label = {"T1D": "1형 당뇨", "T2D": "2형 당뇨"}.get(profile.diabetes_type, "정상")

    target_info = ""
    if profile.target_low and profile.target_high:
        target_info = f"혈당 목표 범위: {profile.target_low:.0f}~{profile.target_high:.0f} mg/dL\n"

    a, b = req.food_a, req.food_b
    return f"""다음 두 음식의 혈당 예측 데이터를 보고 {name_prefix}에게 어느 음식이 더 나은지 한국어로 2문장 이내로 설명해주세요.

[사용자 정보]
당뇨 유형: {diabetes_label}
{target_info}
[음식 A: {a.name}]
예측 최고 혈당: {a.peak_mgdl:.1f} mg/dL (식후 {a.peak_minute}분)
분당 혈당 상승 속도: {a.slope:.2f} mg/dL/min

[음식 B: {b.name}]
예측 최고 혈당: {b.peak_mgdl:.1f} mg/dL (식후 {b.peak_minute}분)
분당 혈당 상승 속도: {b.slope:.2f} mg/dL/min

요구사항:
- "{name_prefix}의 혈당 목표/당뇨 유형을 고려했을 때" 같은 개인화 표현 포함
- 어느 음식이 왜 더 나은지 구체적 수치 근거 포함 (예: "피크가 Xmg/dL 낮아", "상승 속도가 더 완만해")
- 친근한 존댓말, 이모지 없음, 2문장 이내
- 음식 이름을 명시할 것"""


@router.post("/food-compare", response_model=FoodCompareResponse)
async def food_compare(req: FoodCompareRequest):
    """두 음식의 혈당 예측 수치를 비교해 개인화된 설명을 동기 반환한다."""
    prompt = _build_food_compare_prompt(req)
    client = anthropic.Anthropic(api_key=os.getenv("ANTHROPIC_API_KEY"))

    response = await run_in_threadpool(
        call_llm_with_retry,
        client,
        model=_FOOD_COMPARE_MODEL,
        max_tokens=_FOOD_COMPARE_MAX_TOKENS,
        messages=[{"role": "user", "content": prompt}],
    )

    if response is None:
        a, b = req.food_a, req.food_b
        better = a.name if a.slope <= b.slope else b.name
        fallback_msg = f"{better}이(가) 혈당 상승 속도가 더 완만해 더 나은 선택이에요."
        log.warning("food_compare LLM 실패, fallback 반환: user_id=%s", req.user_id)
        return FoodCompareResponse(message=fallback_msg, status="fallback")

    message = response.content[0].text.strip()
    log.info("food_compare 완료: user_id=%s chars=%d", req.user_id, len(message))
    return FoodCompareResponse(message=message, status="success")
