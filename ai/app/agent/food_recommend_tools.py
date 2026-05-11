"""
Glucocoach 음식 추천 Agent — 전용 도구
=======================================
사용자 command(`recommend_food`) 발화 시 호출되는 추천 agent의 tool 셋.
기존 tools.py와 분리. 컨텍스트도 별도(_context).

데이터 소스 (BE 실데이터):
- get_user_food_grades   → GET  /api/agent/users/{id}/food-grades
- get_recent_meals       → GET  /api/agent/users/{id}/recent-meals?days=N
- get_user_profile       → GET  /api/agent/users/{id}/profile
- get_glucose_recent     → GET  /api/agent/users/{id}/glucose-recent

행동:
- send_command_response  → POST /api/agent/notifications  (parentChatMessageId 포함)

모든 호출 X-Agent-Api-Key 헤더 필요 (env: AGENT_API_KEY).
BACKEND_API_URL 미설정 시 fallback dict 반환 (로컬 테스트용).
"""

import os
import requests as _requests


# ── 컨텍스트 (runner가 set) ──────────────────────────────────

_context: dict = {
    "user_id": None,
    "alert_type": "AGENT_FOOD_RECOMMEND",
    "parent_chat_message_id": None,
}


def set_food_agent_context(
    user_id: int, parent_chat_message_id: int, alert_type: str = "AGENT_FOOD_RECOMMEND"
) -> None:
    _context["user_id"] = user_id
    _context["parent_chat_message_id"] = parent_chat_message_id
    _context["alert_type"] = alert_type


def _be_get(path: str, params: dict | None = None) -> dict | list | None:
    """BE GET 호출 헬퍼. 실패 시 None 반환."""
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")
    if not backend_url:
        return None
    try:
        resp = _requests.get(
            f"{backend_url}{path}",
            headers={"X-Agent-Api-Key": agent_api_key},
            params=params or {},
            timeout=5,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        print(f"[FoodRecommend BE GET 실패] {path} err={e}")
        return None


# ── 조회 도구 ─────────────────────────────────────────────


def get_user_food_grades(min_meal_count: int = 2) -> dict:
    """사용자 음식 등급(S/A/B/C/D) 조회. min_meal_count 이상의 기록만."""
    user_id = _context.get("user_id")
    raw = _be_get(f"/api/agent/users/{user_id}/food-grades")
    if raw is None:
        return {"total": 0, "by_grade": {"S": [], "A": [], "B": [], "C": [], "D": []}, "is_cold_start": True, "error": "be_unavailable"}
    filtered = [g for g in raw if (g.get("mealCount") or 0) >= min_meal_count]
    by_grade: dict = {"S": [], "A": [], "B": [], "C": [], "D": []}
    for g in filtered:
        # LLM 노출용이라 foodDisplayName(정리된 표시명) 우선, 없으면 foodName(식약처 raw) fallback.
        item = {
            "food_id": g.get("foodId"),
            "name": g.get("foodDisplayName") or g.get("foodName"),
            "grade": g.get("grade"),
            "avg_slope": float(g.get("avgSlope")) if g.get("avgSlope") is not None else None,
            "meal_count": g.get("mealCount"),
            "image_storage_key": g.get("latestMealImageKey"),
        }
        grade_key = item["grade"] or "C"
        if grade_key in by_grade:
            by_grade[grade_key].append(item)
    return {
        "total": len(filtered),
        "by_grade": by_grade,
        "is_cold_start": len(filtered) < 3,
    }


def get_recent_meals(days: int = 2) -> dict:
    """최근 N일 식사 기록."""
    user_id = _context.get("user_id")
    raw = _be_get(f"/api/agent/users/{user_id}/recent-meals", params={"days": days})
    if raw is None:
        return {"days": days, "meals": [], "error": "be_unavailable"}
    meals = [
        {
            "meal_id": m.get("mealId"),
            "recorded_at": m.get("timestamp"),
            "name": m.get("foodDisplayName") or m.get("foodName"),
            "carbs_g": m.get("carbs"),
            "kcal": m.get("calories"),
            "image_storage_key": m.get("imageStorageKey"),
        }
        for m in raw
    ]
    return {"days": days, "meals": meals}


def get_user_profile() -> dict:
    """사용자 프로필 (당뇨 타입/타겟 범위)."""
    user_id = _context.get("user_id")
    raw = _be_get(f"/api/agent/users/{user_id}/profile")
    if raw is None:
        return {"error": "be_unavailable"}
    return {
        "age": raw.get("age"),
        "gender": raw.get("gender"),
        "diabetes_type": raw.get("diabetesType"),
        "is_medicated": raw.get("isMedicated"),
        "target_low": raw.get("targetLow"),
        "target_high": raw.get("targetHigh"),
        "allergies": [],
    }


def get_unseen_food_candidates(limit: int = 20) -> dict:
    """사용자가 아직 안 먹어본 음식 후보 (foods 테이블, search_count desc).

    신규 음식 추천에만 사용. items[].food_id를 채우기 위해 반드시 이 도구의 결과에서만 신규 음식을 고른다.
    """
    user_id = _context.get("user_id")
    raw = _be_get(f"/api/agent/users/{user_id}/unseen-foods", params={"limit": limit})
    if raw is None:
        return {"candidates": [], "error": "be_unavailable"}
    return {
        "candidates": [
            {
                "food_id": c.get("foodId"),
                "name": c.get("displayName") or c.get("name"),
                "category": c.get("category"),
                "kcal": c.get("kcal"),
                "carbs_g": c.get("carbsG"),
            }
            for c in raw
        ]
    }


def get_glucose_recent() -> dict:
    """최근 혈당 + 마지막 식사 경과 분."""
    user_id = _context.get("user_id")
    raw = _be_get(f"/api/agent/users/{user_id}/glucose-recent")
    if raw is None:
        return {"error": "be_unavailable"}
    return {
        "latest_mg_dl": float(raw["latestMgDl"]) if raw.get("latestMgDl") is not None else None,
        "measured_at": raw.get("measuredAt"),
        "last_meal_min_ago": raw.get("lastMealMinAgo"),
    }


# ── 행동 도구 ─────────────────────────────────────────────


def send_command_response(
    message: str, display_trace: dict, payload: dict | None = None
) -> dict:
    """사용자 command에 대한 agent 응답 발송. parent_id로 user 메시지를 참조한다.

    options는 사용하지 않는다 (텍스트 응답 + 선택적 payload 구조화 카드만).
    BE POST /api/agent/notifications with parentChatMessageId.
    """
    user_id = _context.get("user_id")
    parent_id = _context.get("parent_chat_message_id")
    alert_type = _context.get("alert_type", "AGENT_FOOD_RECOMMEND")
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if not isinstance(display_trace, dict):
        return {"status": "error", "error": "display_trace must be an object"}
    if payload is not None and not isinstance(payload, dict):
        return {"status": "error", "error": "payload must be an object or null"}

    if not backend_url or user_id is None:
        return {
            "status": "sent_local",
            "message": message,
            "display_trace": display_trace,
            "payload": payload,
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
                "options": [],
                "displayTrace": display_trace,
                "payload": payload,
                "parentChatMessageId": parent_id,
            },
            timeout=5,
        )
        print(f"[FoodRecommend API] status={resp.status_code}, body={resp.text}")
        resp.raise_for_status()
        return {"status": "sent", "message": message}
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
        "description": "사용자 프로필 (당뇨 타입/타겟 범위/약 복용 여부). 알레르기 필드는 현재 DB 부재로 빈 배열.",
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "get_glucose_recent",
        "description": "최근 혈당 + 마지막 식사로부터 경과 분. 식후 1시간 이내거나 위험 영역(>200, <70)이면 추천 모드 변경.",
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "get_unseen_food_candidates",
        "description": "사용자가 안 먹어본 음식 후보를 foods 테이블에서 가져온다. search_count 인기순. 신규 음식 추천에만 사용 (items[].food_id를 채우기 위해 필수).",
        "input_schema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "default": 20, "description": "최대 N개 (1~50)"}
            },
        },
    },
    {
        "name": "send_command_response",
        "description": (
            "사용자 command에 대한 agent 응답을 발송한다. parent_id로 user 메시지를 참조하므로 채팅 thread가 형성된다. "
            "options는 사용하지 않는다 (텍스트 + 구조화 payload만). "
            "message는 사용자가 읽을 자연어 본문, payload.items는 FE가 향후 카드로 그릴 음식 메타데이터."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "message": {"type": "string", "description": "사용자에게 보여줄 응답 메시지 본문"},
                "display_trace": {
                    "type": "object",
                    "description": "추론 메타. 지금은 summary 1줄만 필수.",
                    "properties": {
                        "summary": {"type": "string", "description": "어떤 기준으로 골랐는지 1줄"},
                    },
                    "required": ["summary"],
                },
                "payload": {
                    "type": "object",
                    "description": "구조화된 응답 콘텐츠. items 배열만 사용.",
                    "properties": {
                        "items": {
                            "type": "array",
                            "description": "추천 음식 카드. 목표 3개 (데이터 부족 시 1~2개 허용, 위험 영역이면 0개).",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "food_id": {"type": ["integer", "null"], "description": "foods.id. 신규(안 먹어본) 음식이면 null."},
                                    "name": {"type": "string"},
                                    "grade": {"type": ["string", "null"], "description": "S/A/B/C/D. 신규면 null."},
                                    "reason": {"type": "string", "description": "왜 추천하는지 1줄"},
                                    "image_storage_key": {"type": ["string", "null"], "description": "get_user_food_grades에서 받은 image_storage_key 그대로. 신규 음식이면 null."},
                                },
                                "required": ["name", "reason"],
                            },
                        },
                    },
                },
            },
            "required": ["message", "display_trace"],
        },
    },
]


FOOD_RECOMMEND_TOOL_MAP = {
    "get_user_food_grades": get_user_food_grades,
    "get_recent_meals": get_recent_meals,
    "get_user_profile": get_user_profile,
    "get_glucose_recent": get_glucose_recent,
    "get_unseen_food_candidates": get_unseen_food_candidates,
    "send_command_response": send_command_response,
}
