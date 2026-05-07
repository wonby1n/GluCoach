"""
Glucocoach 음식 추천 Agent — 전용 도구
=======================================
사용자 command(`recommend_food`) 발화 시 호출되는 추천 agent의 tool 셋.
기존 tools.py와 분리. 컨텍스트도 별도(_context).

데이터 소스 (시연):
- user_food_grades: 사용자별 음식 등급 S/A/B/C/D + avg_slope (mock)
- recent_meals: 최근 식사 기록 (mock)
- user_profile: 알레르기/선호 (mock — 실제 DB에는 알레르기 컬럼 부재)
- glucose_recent: 최근 혈당 (mock)

행동:
- send_command_response: parentChatMessageId 포함 BE POST → chat_messages INSERT (sender=agent, parent_id 채워짐)
"""

import os
import requests as _requests

# ── 컨텍스트 (runner가 set) ──────────────────────────────────

_context: dict = {
    "user_id": None,
    "alert_type": "AGENT_FOOD_RECOMMEND",
    "parent_chat_message_id": None,
}


def set_food_agent_context(user_id: int, parent_chat_message_id: int, alert_type: str = "AGENT_FOOD_RECOMMEND") -> None:
    _context["user_id"] = user_id
    _context["parent_chat_message_id"] = parent_chat_message_id
    _context["alert_type"] = alert_type


# ── 시연용 Mock 데이터 ─────────────────────────────────────

_MOCK_GRADES = {
    5: [
        {"food_id": 101, "name": "현미밥",      "grade": "S", "avg_slope": 1.2, "meal_count": 8},
        {"food_id": 102, "name": "닭가슴살 샐러드", "grade": "S", "avg_slope": 1.4, "meal_count": 6},
        {"food_id": 103, "name": "두부조림",    "grade": "A", "avg_slope": 1.8, "meal_count": 5},
        {"food_id": 104, "name": "고등어구이",   "grade": "A", "avg_slope": 2.0, "meal_count": 4},
        {"food_id": 105, "name": "잡곡밥",      "grade": "B", "avg_slope": 2.5, "meal_count": 5},
        {"food_id": 201, "name": "흰쌀밥",      "grade": "C", "avg_slope": 3.5, "meal_count": 7},
        {"food_id": 202, "name": "라면",       "grade": "D", "avg_slope": 4.8, "meal_count": 3},
        {"food_id": 203, "name": "떡볶이",     "grade": "D", "avg_slope": 5.1, "meal_count": 2},
    ],
}

_MOCK_RECENT_MEALS = {
    5: [
        {"date": "2026-05-07", "time": "08:00", "name": "현미밥+계란",  "carbs_g": 45},
        {"date": "2026-05-06", "time": "19:30", "name": "닭가슴살 샐러드", "carbs_g": 20},
        {"date": "2026-05-06", "time": "12:00", "name": "흰쌀밥+제육",   "carbs_g": 70},
    ],
}

_MOCK_PROFILE = {
    5: {"age": 45, "gender": "M", "diabetes_type": "2", "diet_pref": "한식", "allergies": []},
}

_MOCK_GLUCOSE_RECENT = {
    5: {"latest_mg_dl": 135, "trend_30m": "stable", "last_meal_min_ago": 180},
}


# ── 조회 도구 ─────────────────────────────────────────────


def get_user_food_grades(min_meal_count: int = 2) -> dict:
    """사용자 음식 등급(S/A/B/C/D) 조회. min_meal_count 이상의 기록만."""
    user_id = _context.get("user_id")
    grades = _MOCK_GRADES.get(user_id, [])
    filtered = [g for g in grades if g["meal_count"] >= min_meal_count]
    by_grade: dict = {"S": [], "A": [], "B": [], "C": [], "D": []}
    for g in filtered:
        by_grade[g["grade"]].append(g)
    return {
        "total": len(filtered),
        "by_grade": by_grade,
        "is_cold_start": len(filtered) < 3,
    }


def get_recent_meals(days: int = 2) -> dict:
    """최근 N일 식사 기록."""
    user_id = _context.get("user_id")
    meals = _MOCK_RECENT_MEALS.get(user_id, [])
    return {"days": days, "meals": meals[: days * 3]}


def get_user_profile() -> dict:
    """사용자 프로필 (당뇨 타입, 식이 선호, 알레르기)."""
    user_id = _context.get("user_id")
    return _MOCK_PROFILE.get(user_id, {})


def get_glucose_recent() -> dict:
    """최근 혈당 + 식후 경과 분."""
    user_id = _context.get("user_id")
    return _MOCK_GLUCOSE_RECENT.get(user_id, {})


# ── 행동 도구 ─────────────────────────────────────────────


def send_command_response(message: str, options: list, display_trace: dict) -> dict:
    """사용자 command에 대한 agent 응답 발송. parent_id로 user 메시지를 참조한다.

    BE POST /api/agent/notifications with parentChatMessageId.
    """
    user_id = _context.get("user_id")
    parent_id = _context.get("parent_chat_message_id")
    alert_type = _context.get("alert_type", "AGENT_FOOD_RECOMMEND")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if not isinstance(options, list) or len(options) > 10:
        return {"status": "error", "error": "options must be 0~10 items", "got": options}
    for opt in options:
        if not isinstance(opt, dict) or "id" not in opt or "label" not in opt:
            return {"status": "error", "error": "each option must be {id, label}", "got": opt}
    if not isinstance(display_trace, dict):
        return {"status": "error", "error": "display_trace must be an object"}

    if not backend_url or user_id is None:
        return {
            "status": "sent_local",
            "message": message,
            "options": options,
            "display_trace": display_trace,
            "parent_chat_message_id": parent_id,
        }

    try:
        resp = _requests.post(
            f"{backend_url}/api/agent/notifications",
            headers={"X-Agent-Api-Key": agent_api_key},
            json={
                "userId": user_id,
                "alertType": alert_type,
                "message": message,
                "options": options,
                "displayTrace": display_trace,
                "parentChatMessageId": parent_id,
            },
            timeout=5,
        )
        print(f"[FoodRecommend API] status={resp.status_code}, body={resp.text}")
        resp.raise_for_status()
        return {"status": "sent", "message": message, "options": options}
    except Exception as e:
        return {"status": "error", "message": message, "error": str(e)}


# ── tool schema ───────────────────────────────────────────

FOOD_RECOMMEND_TOOL_SCHEMAS = [
    {
        "name": "get_user_food_grades",
        "description": "사용자별 음식 등급(S/A/B/C/D)을 조회한다. S/A는 혈당 반응이 좋은 음식, D는 나쁜 음식. is_cold_start=true면 데이터 부족.",
        "input_schema": {
            "type": "object",
            "properties": {
                "min_meal_count": {"type": "integer", "description": "최소 식사 횟수. 기본 2.", "default": 2}
            },
        },
    },
    {
        "name": "get_recent_meals",
        "description": "최근 N일 식사 기록을 조회한다. 직전 식사 시각/내용 파악, 메뉴 중복 회피에 사용.",
        "input_schema": {
            "type": "object",
            "properties": {"days": {"type": "integer", "default": 2}},
        },
    },
    {
        "name": "get_user_profile",
        "description": "사용자 프로필 (당뇨 타입, 식이 선호, 알레르기). 알레르기 음식 추천 회피에 사용.",
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "get_glucose_recent",
        "description": "최근 혈당 + 마지막 식사로부터 경과 분. 식후 1시간 이내거나 위험 영역(>200, <70)이면 추천 모드 변경.",
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "send_command_response",
        "description": (
            "사용자 command에 대한 agent 응답을 발송한다. parent_id로 user 메시지를 참조하므로 채팅 thread가 형성된다. "
            "options는 0~10개, 각 항목은 {id, label}. display_trace는 추론 카드 (summary/cards/decision)."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "message": {"type": "string", "description": "사용자에게 보여줄 응답 메시지"},
                "options": {
                    "type": "array",
                    "minItems": 0,
                    "maxItems": 10,
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "string"},
                            "label": {"type": "string"},
                        },
                        "required": ["id", "label"],
                    },
                },
                "display_trace": {
                    "type": "object",
                    "properties": {
                        "summary": {"type": "string"},
                        "cards": {"type": "array"},
                        "decision": {"type": "object"},
                    },
                },
            },
            "required": ["message", "options", "display_trace"],
        },
    },
]


FOOD_RECOMMEND_TOOL_MAP = {
    "get_user_food_grades": get_user_food_grades,
    "get_recent_meals": get_recent_meals,
    "get_user_profile": get_user_profile,
    "get_glucose_recent": get_glucose_recent,
    "send_command_response": send_command_response,
}
