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
from app.agent.projection_client import send_command as _send_projection


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
    return {
        "hours": hours,
        "notifications": NOTIFICATION_HISTORY,
    }


# ── 행동 함수 2개 ──────────────────────────────────────────


def send_notification(message: str) -> dict:
    """사용자에게 알림 메시지를 발송한다. BACKEND_API_URL 설정 시 실제 FCM 발송."""
    user_id = _agent_context.get("user_id")
    alert_type = _agent_context.get("alert_type", "AGENT_GENERIC")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if not backend_url or user_id is None:
        return {"status": "sent", "message": message}

    try:
        resp = _requests.post(
            f"{backend_url}/api/agent/notifications",
            headers={"X-Agent-Api-Key": agent_api_key},
            json={"userId": user_id, "alertType": alert_type, "message": message},
            timeout=5,
        )
        print(f"[API 응답] status={resp.status_code}, body={resp.text}")  # 추가
        resp.raise_for_status()
        return {"status": "sent", "message": message}
    except Exception as e:
        return {"status": "error", "message": message, "error": str(e)}


def schedule_followup(delay_minutes: int, reason: str) -> dict:
    """지정한 시간 후에 agent를 다시 호출하도록 예약한다. BACKEND_API_URL 설정 시 실제 BE API 호출."""
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


def trigger_projection(command: str, sleep_score: str = "", glucose: str = "") -> dict:
    """라즈베리파이 프로젝션 서버에 명령을 전송한다.
    command: SHOW | HIDE | BRIEFING | ALERT
    BRIEFING 사용 시 sleep_score와 glucose 값을 함께 전달한다.
    """
    if command == "BRIEFING":
        cmd = f"BRIEFING:{sleep_score}:{glucose}"
    else:
        cmd = command
    return _send_projection(cmd)


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
        "description": "사용자에게 알림 메시지를 발송한다. agent의 최종 행동으로 사용한다. 메시지는 후보 3개 중 사용자 컨텍스트에 가장 적절한 하나를 선택해 전달한다. 사용자 응답 옵션은 시스템에서 자동으로 표시되므로 별도 지정하지 않아도 된다.",
        "input_schema": {
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "발송할 알림 메시지 본문 (한국어, 친근한 톤)",
                },
            },
            "required": ["message"],
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
    {
        "name": "trigger_projection",
        "description": "라즈베리파이 프로젝터에 영상 명령을 전송한다. send_notification 직전에 호출하여 알림 내용에 맞는 영상을 프로젝터에 표시한다. BRIEFING: 아침 브리핑 영상 1회 재생. ALERT: 고혈당 경고 영상 루프. SHOW: 대기 영상 루프. HIDE: 영상 종료.",
        "input_schema": {
            "type": "object",
            "properties": {
                "command": {
                    "type": "string",
                    "enum": ["SHOW", "HIDE", "BRIEFING", "ALERT"],
                    "description": "프로젝션 명령. BRIEFING은 sleep_score와 glucose도 함께 전달한다.",
                },
                "sleep_score": {
                    "type": "string",
                    "description": "BRIEFING 시 수면 점수 (예: '85'). BRIEFING 외에는 생략 가능.",
                },
                "glucose": {
                    "type": "string",
                    "description": "BRIEFING 시 현재 혈당값 (예: '112'). BRIEFING 외에는 생략 가능.",
                },
            },
            "required": ["command"],
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
    "trigger_projection": trigger_projection,
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
    _print("send_notification('test message')",
           send_notification("테스트 메시지"))

    _print("schedule_followup(30, 'meeting')",
           schedule_followup(30, "회의 중"))
