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

    diabetes_label = {"T1D": "1형 당뇨", "T2D": "2형 당뇨"}.get(profile.diabetes_type, "정상 혈당")

    bmi_label = ""
    if profile.bmi:
        bmi_val = profile.bmi
        if bmi_val < 18.5:
            bmi_cat = "저체중"
        elif bmi_val < 23.0:
            bmi_cat = "정상 체중"
        elif bmi_val < 25.0:
            bmi_cat = "과체중"
        elif bmi_val < 30.0:
            bmi_cat = "비만"
        else:
            bmi_cat = "고도비만"
        bmi_label = f"BMI {bmi_val:.1f}({bmi_cat})"

    age_label = f"{profile.age}세" if profile.age else ""
    med_label = "혈당 조절 투약 중" if profile.is_medicated else ""

    persona_parts = [p for p in [diabetes_label, bmi_label, age_label, med_label] if p]
    persona_str = " / ".join(persona_parts)

    target_info = ""
    if profile.target_low and profile.target_high:
        target_info = f"혈당 목표: {profile.target_low:.0f}~{profile.target_high:.0f} mg/dL\n"

    a, b = req.food_a, req.food_b
    peak_diff = abs(a.peak_mgdl - b.peak_mgdl)
    better = a if a.peak_mgdl <= b.peak_mgdl else b
    return f"""두 음식의 혈당 예측 데이터를 보고 아래 형식으로만 답변하세요.

[사용자] {name_prefix} / {persona_str}{(' / ' + target_info.strip()) if target_info.strip() else ''}
[{a.name}] 피크 {a.peak_mgdl:.0f} mg/dL (식후 {a.peak_minute}분) / 상승 {a.slope:.2f} mg/dL/min
[{b.name}] 피크 {b.peak_mgdl:.0f} mg/dL (식후 {b.peak_minute}분) / 상승 {b.slope:.2f} mg/dL/min

출력 형식:
추천 음식 : {better.name}
이유 : [문장1: 사용자 특성(당뇨유형·BMI·투약 중 가장 관련 있는 1가지)을 한 문장으로. 예) "정상 혈당에 저체중이신 {name_prefix}은 급격한 혈당 상승에 더 취약해요." / 문장2: {better.name}의 피크가 {peak_diff:.0f}mg/dL 낮고 상승 속도가 얼마나 더 완만한지 수치로만. 예) "피크가 {peak_diff:.0f}mg/dL 낮고 상승 속도도 더 완만해요."]

규칙: 두 문장 모두 해요체 / 이모지 없음 / 형식 외 텍스트 금지"""


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
