"""
캘린더 일정 기반 혈당 관리 알림 Agent
=======================================
FE 캘린더 일정 제목 → LLM → 키키 메시지 → BE /api/agent/notifications
"""

import logging
import os

import anthropic
import requests

from app.agent.fallback import call_llm_with_retry

log = logging.getLogger(__name__)

MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 150

FALLBACK_MESSAGE = "오늘 일정이 있어요! 외출 전 혈당을 한 번 체크해보는 건 어떨까요? 💪"


def run_calendar_reminder_agent(user_id: int, parent_chat_message_id: int, payload: dict) -> dict:
    events_raw = payload.get("events", "")
    if not events_raw:
        log.warning("calendar_reminder: events payload 비어 있음 userId=%s", user_id)
        return {"error": "no_events"}

    event_list = [e.strip() for e in str(events_raw).split(",") if e.strip()][:3]
    event_text = ", ".join(f"'{e}'" for e in event_list)

    client = anthropic.Anthropic()
    response = call_llm_with_retry(
        client,
        model=MODEL,
        max_tokens=MAX_TOKENS,
        messages=[
            {
                "role": "user",
                "content": (
                    "당신은 혈당 관리 AI 코치 '키키'입니다.\n"
                    f"사용자의 오늘/내일 캘린더 일정: {event_text}\n"
                    "일정을 바탕으로 혈당 관리 관점에서 친근하고 짧은 한마디를 해주세요. "
                    "60자 이내, 이모지 1개, 구체적 일정 이름을 언급. "
                    "예시: '오늘 지민이랑 점심 있네요! 외식 전 혈당 체크해볼까요? 🍽️'"
                ),
            }
        ],
    )

    if response is None:
        message = FALLBACK_MESSAGE
        log.warning("calendar_reminder: LLM 실패, fallback 사용 userId=%s", user_id)
    else:
        message = response.content[0].text.strip()
        log.info("calendar_reminder: 메시지 생성 완료 userId=%s", user_id)

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

    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if not backend_url:
        return {"status": "success", "message": message}

    try:
        resp = requests.post(
            f"{backend_url}/api/agent/notifications",
            headers={"X-Agent-Api-Key": agent_api_key},
            json={
                "userId": user_id,
                "alertType": "CALENDAR_REMINDER",
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
