"""
캘린더 일정 기반 혈당 관리 알림 Agent
=======================================
일정 제목+시간 분석:
- 식사/음식 관련 일정 → 음식 성적표 기반 음식 추천
- 그 외 일정 → 행동 추천 메시지
"""

import logging
import os

import anthropic
import requests

from app.agent.fallback import call_llm_with_retry
from app.agent.food_recommend_tools import (
    get_recent_meals,
    get_user_food_grades,
    set_food_agent_context,
)

log = logging.getLogger(__name__)

MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 200

FALLBACK_MESSAGE = "오늘 일정이 있어요! 외출 전 혈당을 한 번 체크해보는 건 어떨까요? 💪"


def _post_notification(user_id: int, message: str, event_list: list) -> dict:
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    display_trace = {
        "summary": f"캘린더 일정 분석: {', '.join(event_list)}",
        "cards": [
            {
                "type": "calendar",
                "title": "오늘의 일정",
                "description": ", ".join(event_list),
                "severity": "normal",
            }
        ],
        "decision": {"reason": "캘린더 일정 기반 혈당 관리 알림"},
    }

    if not backend_url:
        return {"status": "success", "message": message}

    try:
        resp = requests.post(
            f"{backend_url}/api/agent/notifications",
            headers={"X-Agent-Api-Key": agent_api_key},
            json={
                "userId": user_id,
                "alertType": "AGENT_CALENDAR_REMINDER",
                "message": message,
                "displayTrace": display_trace,
            },
            timeout=5,
        )
        resp.raise_for_status()
        return {"status": "success", "message": message}
    except Exception as e:
        log.warning("calendar_reminder: 알림 전송 실패 userId=%s err=%s", user_id, e)
        return {"status": "error", "message": message, "error": str(e)}


def run_calendar_reminder_agent(user_id: int, parent_chat_message_id: int, payload: dict) -> dict:
    events_raw = payload.get("events", "")
    if not events_raw:
        log.warning("calendar_reminder: events payload 비어 있음 userId=%s", user_id)
        return {"error": "no_events"}

    event_list = [e.strip() for e in str(events_raw).split(",") if e.strip()][:3]
    event_text = ", ".join(f"'{e}'" for e in event_list)

    client = anthropic.Anthropic()

    # Step 1: 식사 관련 여부 분류 — 식사 관련이면 [FOOD], 아니면 행동 추천 메시지 직접 생성
    # Step 1: 식사 관련 여부만 분류 (max_tokens 최소화로 프롬프트 누출 방지)
    classify_response = call_llm_with_retry(
        client,
        model=MODEL,
        max_tokens=10,
        messages=[
            {
                "role": "user",
                "content": (
                    f"캘린더 일정: {event_text}\n\n"
                    "식사/음식 관련(점심 약속, 회식, 카페, 저녁 모임 등)이면 [FOOD], 아니면 [ACTION] 출력."
                ),
            }
        ],
    )

    is_food = classify_response is not None and "[FOOD]" in classify_response.content[0].text.upper()

    if not is_food:
        # Step 1b: 행동 추천 메시지 생성 (분리된 호출)
        action_response = call_llm_with_retry(
            client,
            model=MODEL,
            max_tokens=MAX_TOKENS,
            messages=[
                {
                    "role": "user",
                    "content": (
                        "당신은 혈당 관리 AI 코치 '키키'입니다.\n"
                        f"사용자의 캘린더 일정: {event_text}\n\n"
                        "이 일정과 관련된 혈당 관리 행동 추천 메시지를 60자 이내로 작성하세요. "
                        "이모지 1개 포함. 메시지 본문만 출력하세요.\n"
                        "예시: 오늘 헬스장 가는 날이에요! 운동 전 혈당 꼭 체크해요 💪"
                    ),
                }
            ],
        )
        if action_response is None:
            body = FALLBACK_MESSAGE
        else:
            body = action_response.content[0].text.strip()
        message = f"일정 : {event_list[0]}\n\n{body}"
        log.info("calendar_reminder: 행동 추천 완료 userId=%s msg=%s", user_id, message)
        return _post_notification(user_id, message, event_list)

    # Step 2: 식사 관련 → 음식 성적표 조회 후 음식 추천 생성
    log.info("calendar_reminder: 식사 관련 일정 감지 — 음식 추천 생성 userId=%s", user_id)
    set_food_agent_context(
        user_id=user_id,
        parent_chat_message_id=parent_chat_message_id,
        alert_type="AGENT_CALENDAR_REMINDER",
    )

    food_grades = get_user_food_grades()
    recent_meals = get_recent_meals(days=2)  # noqa: F841 (컨텍스트용)

    good_foods = food_grades.get("by_grade", {}).get("S", []) + food_grades.get("by_grade", {}).get("A", [])
    bad_foods = food_grades.get("by_grade", {}).get("D", [])
    good_names = [f["name"] for f in good_foods[:5] if f.get("name")]
    bad_names = [f["name"] for f in bad_foods[:3] if f.get("name")]

    food_context = ""
    if good_names:
        food_context += f"혈당 반응 좋은 음식: {', '.join(good_names)}. "
    if bad_names:
        food_context += f"혈당 급등 음식: {', '.join(bad_names)}. "
    if not food_context:
        food_context = "식사 이력 데이터 부족. "

    food_response = call_llm_with_retry(
        client,
        model=MODEL,
        max_tokens=MAX_TOKENS,
        messages=[
            {
                "role": "user",
                "content": (
                    "당신은 혈당 관리 AI 코치 '키키'입니다.\n"
                    f"사용자의 일정: {event_text}\n"
                    f"사용자 음식 성적표: {food_context}\n"
                    "외식 시 혈당 관리 음식 추천 메시지를 70자 이내로 작성하세요. "
                    "이모지 1개 포함. 추천/주의 음식을 언급하세요. 메시지 본문만 출력하세요.\n"
                    "예시: 혈당 올리는 라면보다 비빔밥이 어때요? 밥 양을 절반으로 줄이면 더 좋아요 🍚"
                ),
            }
        ],
    )

    if food_response is None:
        log.warning("calendar_reminder: 음식 추천 LLM 실패, fallback 사용 userId=%s", user_id)
        return _post_notification(user_id, FALLBACK_MESSAGE, event_list)

    body = food_response.content[0].text.strip()
    message = f"일정 : {event_list[0]}\n\n{body}"
    log.info("calendar_reminder: 음식 추천 완료 userId=%s", user_id)
    return _post_notification(user_id, message, event_list)
