"""
Glucoach Agent Mock Tools
=========================
LLM tool-calling 시 호출되는 mock 함수 7개.
시연용으로, 파라미터와 무관하게 mock_data의 고정값을 반환한다.

함수 시그니처는 tool schema 기준:
  - get_glucose(start_time, end_time)
  - get_sleep(date)
  - get_meals(date)
  - get_steps(start_time, end_time)
  - get_notification_history(hours)
  - send_notification(message)
  - schedule_followup(delay_minutes, reason)
"""

import os
import requests as _requests

from app.agent.mock_data import (
    GLUCOSE_DATA,
    GLUCOSE_AVERAGES,
    SLEEP_DATA,
    MEAL_DATA,
    STEPS_DATA,
    NOTIFICATION_HISTORY,
)


# ── Agent 컨텍스트 (runner가 실행 전에 set) ─────────────────

_agent_context: dict = {"user_id": None, "alert_type": "AGENT_GENERIC"}


def set_agent_context(user_id: int, alert_type: str) -> None:
    _agent_context["user_id"] = user_id
    _agent_context["alert_type"] = alert_type


# ── 유틸 ───────────────────────────────────────────────────


def _norm(t: str) -> str:
    """'2026-05-03T18:00:00' -> '2026-05-03 18:00' (mock_data 키 형식에 맞춤)"""
    t = t.replace("T", " ")
    parts = t.split(":")
    if len(parts) == 3:
        t = ":".join(parts[:2])
    return t


# ── 조회 함수 5개 ──────────────────────────────────────────


def get_glucose(start_time: str, end_time: str) -> dict:
    """지정한 시간 범위의 혈당 측정 데이터를 반환한다."""
    start_time, end_time = _norm(start_time), _norm(end_time)
    readings = [
        {"time": t, "glucose_mg_dl": v}
        for t, v in GLUCOSE_DATA.items()
        if start_time <= t <= end_time
    ]
    return {
        "readings": readings,
        "averages": GLUCOSE_AVERAGES,
    }


def get_sleep(date: str) -> dict:
    """지정한 날짜의 수면 데이터를 반환한다."""
    return {
        "date": date,
        "sleep": SLEEP_DATA.get(date, {}),
        "average_7d": SLEEP_DATA["average_7d"],
    }


def get_meals(date: str) -> dict:
    """지정한 날짜의 식사 기록을 반환한다."""
    user_id = _agent_context.get("user_id")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if backend_url and user_id is not None:
        try:
            resp = _requests.get(
                f"{backend_url}/api/agent/meals",
                headers={"X-Agent-Api-Key": agent_api_key},
                params={"user_id": user_id, "date": date},
                timeout=5,
            )
            resp.raise_for_status()
            return {"date": date, "meals": resp.json()}
        except Exception as e:
            print(f"[get_meals] backend error, fallback to mock: {e}")

    return {
        "date": date,
        "meals": MEAL_DATA.get(date, []),
    }


def get_steps(start_time: str, end_time: str) -> dict:
    """지정한 시간 범위의 걸음수 합계를 반환한다."""
    start_time, end_time = _norm(start_time), _norm(end_time)
    matched = [
        entry for entry in STEPS_DATA
        if entry["start"] >= start_time and entry["end"] <= end_time
    ]
    total = sum(entry["steps"] for entry in matched)
    return {
        "start_time": start_time,
        "end_time": end_time,
        "total_steps": total,
        "periods": matched,
    }


def get_notification_history(hours: int) -> dict:
    """최근 N시간 동안 발송된 알림 이력을 반환한다."""
    from datetime import datetime, timedelta
    from app.agent.mock_data import DEMO_DATE

    # 시연 날짜 기준 "현재 시각"을 07:00으로 고정
    reference = datetime.strptime(f"{DEMO_DATE['today']} 07:00", "%Y-%m-%d %H:%M")
    cutoff = reference - timedelta(hours=hours)

    filtered = [
        n for n in NOTIFICATION_HISTORY
        if datetime.strptime(n["sent_at"], "%Y-%m-%d %H:%M") > cutoff
    ]
    return {
        "hours": hours,
        "today": DEMO_DATE["today"],   # Claude가 날짜 비교해 중복 여부 판단하도록
        "notifications": filtered,
    }


# ── 행동 함수 2개 ──────────────────────────────────────────


def send_notification(message: str, options: list, display_trace: dict) -> dict:
    """사용자에게 알림 메시지 + 응답 선택지 3개 + AI 추론 카드를 발송한다.

    options: [{"id": str, "label": str}, ...] 정확히 3개
    display_trace: {"summary": str, "cards": [...], "decision": {"reason": str}}
    """
    user_id = _agent_context.get("user_id")
    alert_type = _agent_context.get("alert_type", "AGENT_GENERIC")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    # ── 입력 검증 ──────────────────────────────────────
    if not isinstance(options, list):
        options = []
    if len(options) > 0 and len(options) != 3:
        return {"status": "error", "error": "options must be exactly 3 items or empty", "got": options}
    for opt in options:
        if not isinstance(opt, dict) or "id" not in opt or "label" not in opt:
            return {"status": "error", "error": "each option must be {id, label}", "got": opt}
    if not isinstance(display_trace, dict):
        return {"status": "error", "error": "display_trace must be an object"}

    if not backend_url or user_id is None:
        return {
            "status": "sent",
            "message": message,
            "options": options,
            "display_trace": display_trace,
        }

    try:
        body = {
            "userId": user_id,
            "alertType": alert_type,
            "message": message,
            "displayTrace": display_trace,
        }
        if options:
            body["options"] = options
        resp = _requests.post(
            f"{backend_url}/api/agent/notifications",
            headers={"X-Agent-Api-Key": agent_api_key},
            json=body,
            timeout=5,
        )
        print(f"[API 응답] status={resp.status_code}, body={resp.text}")
        resp.raise_for_status()
        return {"status": "sent", "message": message, "options": options}
    except Exception as e:
        return {"status": "error", "message": message, "error": str(e)}


def schedule_followup(delay_minutes: int, reason: str) -> dict:
    """지정한 시간 후에 agent를 다시 호출하도록 예약한다. BACKEND_API_URL 설정 시 실제 BE API 호출."""
    delay_minutes = 1  # 시연 모드: 항상 1분 후 재호출
    user_id = _agent_context.get("user_id")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if not backend_url or user_id is None:
        return {"status": "scheduled", "delay_minutes": delay_minutes, "reason": reason}

    try:
        resp = _requests.post(
            f"{backend_url}/api/agent/schedule_followup",
            headers={"X-Agent-Api-Key": agent_api_key},
            json={
                "userId": user_id,
                "triggerType": "post_meal_followup",
                "delayMinutes": delay_minutes,
                "reason": reason,
            },
            timeout=5,
        )
        resp.raise_for_status()
        return {"status": "scheduled", "delay_minutes": delay_minutes, "reason": reason}
    except Exception as e:
        return {"status": "error", "delay_minutes": delay_minutes, "reason": reason, "error": str(e)}


# ── tool schema (LLM tools 파라미터용) ─────────────────────


TOOL_SCHEMAS = [
    {
        "name": "get_glucose",
        "description": "지정한 시간 범위의 혈당 측정 데이터를 조회한다. 혈당값 리스트를 반환한다. 혈당 추세, 변동폭, 식후 반응 등을 분석할 때 사용한다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "start_time": {
                    "type": "string",
                    "description": "조회 시작 시각 (ISO 8601 형식, 예: 2026-05-09T18:00:00)",
                },
                "end_time": {
                    "type": "string",
                    "description": "조회 종료 시각 (ISO 8601 형식, 예: 2026-05-10T07:00:00)",
                },
            },
            "required": ["start_time", "end_time"],
        },
    },
    {
        "name": "get_sleep",
        "description": "지정한 날짜의 수면 데이터를 조회한다. 해당 날짜 밤의 수면 시간(시간 단위)과 사용자의 평소 평균 수면 시간을 함께 반환한다. 수면 부족 여부 판단에 사용한다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "date": {
                    "type": "string",
                    "description": "조회 날짜 (YYYY-MM-DD 형식, 예: 2026-05-09)",
                },
            },
            "required": ["date"],
        },
    },
    {
        "name": "get_meals",
        "description": "지정한 날짜의 식사 기록을 조회한다. 식사 시각, 음식명, 영양 정보(탄수화물, 칼로리)를 포함한다. 어제 저녁 메뉴, 오늘 점심 등 식사 맥락 파악에 사용한다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "date": {
                    "type": "string",
                    "description": "조회 날짜 (YYYY-MM-DD 형식, 예: 2026-05-09)",
                },
            },
            "required": ["date"],
        },
    },
    {
        "name": "get_steps",
        "description": "지정한 시간 범위의 걸음수 합계를 조회한다. 식후 활동량 확인, 사용자 움직임 감지 등에 사용한다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "start_time": {
                    "type": "string",
                    "description": "조회 시작 시각 (ISO 8601 형식)",
                },
                "end_time": {
                    "type": "string",
                    "description": "조회 종료 시각 (ISO 8601 형식)",
                },
            },
            "required": ["start_time", "end_time"],
        },
    },
    {
        "name": "get_notification_history",
        "description": "최근 N시간 동안 사용자에게 발송된 알림 이력을 조회한다. 중복 알림 방지, 이전 컨텍스트 파악, schedule_followup 예약 여부 확인 등에 사용한다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "hours": {
                    "type": "integer",
                    "description": "현재 시각으로부터 몇 시간 전까지 조회할지 (예: 2 = 최근 2시간)",
                },
            },
            "required": ["hours"],
        },
    },
    {
        "name": "send_notification",
        "description": (
            "사용자에게 알림 메시지 + AI 추론 카드를 발송한다. "
            "agent의 최종 행동으로 사용한다. "
            "meal_recorded 트리거에서는 options에 정확히 3개의 선택지를 포함해야 한다. "
            "user_response/schedule_followup 트리거에서는 options를 빈 배열 []로 전달한다. "
            "display_trace는 사용자에게 노출 가능한 추론 과정 카드다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "발송할 알림 메시지 본문 (한국어, 친근한 톤, 60자 이내)",
                },
                "options": {
                    "type": "array",
                    "minItems": 0,
                    "maxItems": 3,
                    "description": (
                        "사용자 응답 선택지. 첫 알림(meal_recorded)에서는 정확히 3개, "
                        "user_response/schedule_followup에서는 빈 배열 []로 전달. "
                        "관례적 순서: 1) 긍정/수락 2) 미루기/나중에 3) 거절/패스. "
                        "label은 8자 이내 짧게."
                    ),
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {
                                "type": "string",
                                "description": "응답 식별자 (snake_case, 예: walk_now, later_30, skip)",
                            },
                            "label": {
                                "type": "string",
                                "description": "버튼에 표시될 한국어 라벨 (8자 이내)",
                            },
                        },
                        "required": ["id", "label"],
                    },
                },
                "display_trace": {
                    "type": "object",
                    "description": (
                        "AI 추론 과정 카드 (사용자 노출 가능). 사용자가 메시지를 받은 이유를 "
                        "이해할 수 있게 한다."
                    ),
                    "properties": {
                        "summary": {
                            "type": "string",
                            "description": "1줄 요약 (예: '식후 60분, 활동량 적음')",
                        },
                        "cards": {
                            "type": "array",
                            "description": "확인한 신호 카드 N개. 비어있어도 됨.",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "type": {
                                        "type": "string",
                                        "description": "신호 종류 (glucose/sleep/meal/steps 등)",
                                    },
                                    "title": {"type": "string", "description": "카드 제목"},
                                    "description": {
                                        "type": "string",
                                        "description": "카드 본문. 구체적인 수치와 항목명을 포함해 사용자가 이해하기 쉽게 작성. 예: '어젯밤 수면은 5시간으로, 평소보다 2시간 부족했어요.'",
                                    },
                                },
                                "required": ["type", "title", "description"],
                            },
                        },
                        "decision": {
                            "type": "object",
                            "description": "메시지 선택 이유",
                            "properties": {
                                "reason": {"type": "string"},
                            },
                            "required": ["reason"],
                        },
                    },
                    "required": ["summary", "cards", "decision"],
                },
            },
            "required": ["message", "options", "display_trace"],
        },
    },
    {
        "name": "schedule_followup",
        "description": "지정한 시간 후에 agent를 자동으로 다시 호출하도록 예약한다. 사용자가 '나중에', '회의 중' 등의 응답을 했을 때 적절한 시간 뒤 재시도하기 위해 사용한다. delay_minutes는 사용자 응답 맥락에 따라 결정한다 (회의 중→30분, 나중에→30분).",
        "input_schema": {
            "type": "object",
            "properties": {
                "delay_minutes": {
                    "type": "integer",
                    "description": "지금부터 몇 분 후에 agent를 재호출할지",
                },
                "reason": {
                    "type": "string",
                    "description": "재호출 이유 및 컨텍스트 (다음 agent 호출 시 input으로 전달됨, 예: '사용자 회의 중, 회의 종료 후 활동 재권유')",
                },
            },
            "required": ["delay_minutes", "reason"],
        },
    },
]

# ── tool name → 함수 매핑 (dispatcher용) ───────────────────

TOOL_MAP = {
    "get_glucose": get_glucose,
    "get_sleep": get_sleep,
    "get_meals": get_meals,
    "get_steps": get_steps,
    "get_notification_history": get_notification_history,
    "send_notification": send_notification,
    "schedule_followup": schedule_followup,
}


# ── 테스트 ─────────────────────────────────────────────────

if __name__ == "__main__":
    import json
    import sys

    sys.stdout.reconfigure(encoding="utf-8")

    def _print(label: str, result: dict) -> None:
        print(f"\n{'='*50}")
        print(f" {label}")
        print(f"{'='*50}")
        print(json.dumps(result, indent=2, ensure_ascii=False))

    # 조회 함수
    _print("get_sleep('2026-05-03')",
           get_sleep("2026-05-03"))

    _print("get_glucose('2026-05-03 18:00' ~ '2026-05-04 08:00')",
           get_glucose("2026-05-03 18:00", "2026-05-04 08:00"))

    _print("get_meals('2026-05-04')",
           get_meals("2026-05-04"))

    _print("get_steps('2026-05-04 12:30' ~ '2026-05-04 13:00') - expect 23",
           get_steps("2026-05-04 12:30", "2026-05-04 13:00"))

    # ISO 8601 T 포맷 테스트
    _print("get_glucose(T format) - expect 16 readings",
           get_glucose("2026-05-03T18:00:00", "2026-05-04T08:00:00"))

    _print("get_steps(T format) - expect 23",
           get_steps("2026-05-04T12:30:00", "2026-05-04T13:00:00"))

    _print("get_notification_history(24)",
           get_notification_history(24))

    # 행동 함수
    _print(
        "send_notification('test message', options, display_trace)",
        send_notification(
            "테스트 메시지",
            [
                {"id": "ack", "label": "알겠어요"},
                {"id": "later_30", "label": "30분 뒤"},
                {"id": "skip", "label": "패스"},
            ],
            {
                "summary": "테스트 요약",
                "cards": [],
                "decision": {"reason": "테스트"},
            },
        ),
    )

    _print("schedule_followup(30, 'meeting')",
           schedule_followup(30, "회의 중"))
