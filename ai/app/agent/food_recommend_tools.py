"""
Glucocoach 음식 추천 Agent — 전용 도구
=======================================
사용자 command(`recommend_food`) 발화 시 호출되는 추천 agent의 tool 셋.
기존 tools.py와 분리. 컨텍스트도 별도 (ContextVar 사용 — 동시 요청 isolation).

데이터 소스 (BE 실데이터):
- get_user_food_grades   → GET  /api/agent/users/{id}/food-grades
- get_recent_meals       → GET  /api/agent/users/{id}/recent-meals?days=N
- get_user_profile       → GET  /api/agent/users/{id}/profile
- get_glucose_recent     → GET  /api/agent/users/{id}/glucose-recent
- get_today_activity     → GET  /api/agent/steps + /api/agent/sleep  (오늘 걸음수 + 어젯밤 수면)

행동:
- send_command_response  → POST /api/agent/notifications  (parentChatMessageId 포함)

모든 호출 X-Agent-Api-Key 헤더 필요 (env: AGENT_API_KEY).
BACKEND_API_URL 미설정 시 fallback dict 반환 (로컬 테스트용).
"""

import logging
import os
from contextvars import ContextVar
from datetime import datetime
from zoneinfo import ZoneInfo

import requests as _requests

log = logging.getLogger(__name__)


# ── 컨텍스트 (runner가 set) ──────────────────────────────────
# ContextVar 로 분리해 fastapi run_in_threadpool 동시 요청 시 user_id 가 서로 덮어쓰이는 race 차단.
# anyio.to_thread.run_sync 가 호출 시점의 Context 를 워커 스레드로 그대로 복사하므로, 각 요청 핸들러
# 내부에서 set_food_agent_context() → run_food_recommend_agent() 호출 흐름이 isolation 된다.

_user_id_var: ContextVar[int | None] = ContextVar("food_user_id", default=None)
_parent_chat_message_id_var: ContextVar[int | None] = ContextVar(
    "food_parent_chat_message_id", default=None
)
_alert_type_var: ContextVar[str] = ContextVar(
    "food_alert_type", default="AGENT_FOOD_RECOMMEND"
)


def set_food_agent_context(
    user_id: int, parent_chat_message_id: int, alert_type: str = "AGENT_FOOD_RECOMMEND"
) -> None:
    _user_id_var.set(user_id)
    _parent_chat_message_id_var.set(parent_chat_message_id)
    _alert_type_var.set(alert_type)


def _ctx_user_id() -> int | None:
    return _user_id_var.get()


def _ctx_parent_chat_message_id() -> int | None:
    return _parent_chat_message_id_var.get()


def _ctx_alert_type() -> str:
    return _alert_type_var.get()


_BE_URL_WARNED = False


def _be_get(path: str, params: dict | None = None) -> dict | list | None:
    """BE GET 호출 헬퍼. 실패 시 None 반환 + 로그.

    BACKEND_API_URL 미설정 시 첫 호출에서 한 번만 경고 (로컬 테스트 fallback 모드).
    HTTP 실패는 path/status/body 를 묶어 warning 으로 노출 — 운영에서 silent 흡수 방지.
    """
    global _BE_URL_WARNED
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")
    if not backend_url:
        if not _BE_URL_WARNED:
            log.warning(
                "[FoodRecommend] BACKEND_API_URL 미설정 — BE 호출 모두 None 반환 (로컬 fallback 모드)"
            )
            _BE_URL_WARNED = True
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
    except _requests.HTTPError as e:
        status = getattr(e.response, "status_code", "?")
        body = getattr(e.response, "text", "")[:200]
        log.warning(
            "[FoodRecommend BE GET HTTP %s] path=%s params=%s body=%s",
            status,
            path,
            params,
            body,
        )
        return None
    except _requests.RequestException as e:
        log.warning("[FoodRecommend BE GET 네트워크 실패] path=%s err=%s", path, e)
        return None
    except Exception as e:
        log.exception("[FoodRecommend BE GET 예상치 못한 오류] path=%s err=%s", path, e)
        return None


# ── 조회 도구 ─────────────────────────────────────────────


def get_user_food_grades(min_meal_count: int = 2) -> dict:
    """사용자 음식 등급(S/A/B/C/D) 조회. min_meal_count 이상의 기록만."""
    user_id = _ctx_user_id()
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
    user_id = _ctx_user_id()
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
    user_id = _ctx_user_id()
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


def get_unseen_food_candidates(limit: int = 5) -> dict:
    """사용자가 아직 안 먹어본 음식 후보 (foods 테이블, search_count desc).

    신규 음식 추천에만 사용. items[].food_id를 채우기 위해 반드시 이 도구의 결과에서만 신규 음식을 고른다.
    """
    user_id = _ctx_user_id()
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
    user_id = _ctx_user_id()
    raw = _be_get(f"/api/agent/users/{user_id}/glucose-recent")
    if raw is None:
        return {"error": "be_unavailable"}
    return {
        "latest_mg_dl": float(raw["latestMgDl"]) if raw.get("latestMgDl") is not None else None,
        "measured_at": raw.get("measuredAt"),
        "last_meal_min_ago": raw.get("lastMealMinAgo"),
    }


def get_today_activity() -> dict:
    """오늘 누적 걸음수 + 어젯밤 수면 분 + 7일 평균 수면 분.

    삼성헬스/HealthConnect → daily_health_summaries / step_records 에 적재된 값을 조회.
    음식 가부 판단 시 "오늘 활동량/컨디션" 보정 신호로 사용한다 (룰은 프롬프트 참조).
    """
    user_id = _ctx_user_id()
    if user_id is None:
        return {"error": "missing_user"}
    kst_now = datetime.now(ZoneInfo("Asia/Seoul"))
    today_local = kst_now.date()
    start_local = datetime.combine(today_local, datetime.min.time())
    # 백엔드는 @DateTimeFormat ISO.DATE_TIME (LocalDateTime) — zone offset 없는 ISO 문자열 전달.
    steps_raw = _be_get(
        "/api/agent/steps",
        params={
            "user_id": user_id,
            "start": start_local.isoformat(timespec="seconds"),
            "end": kst_now.replace(tzinfo=None).isoformat(timespec="seconds"),
        },
    )
    sleep_raw = _be_get(
        "/api/agent/sleep",
        params={"user_id": user_id, "date": today_local.isoformat()},
    )
    steps_today = (steps_raw or {}).get("windowSteps") or 0
    sleep_minutes = (sleep_raw or {}).get("sleepMinutes") or 0
    avg_sleep = (sleep_raw or {}).get("averageSleepMinutes") or 0.0
    return {
        "steps_today": int(steps_today),
        "sleep_minutes_last_night": int(sleep_minutes),
        "avg_sleep_minutes_7d": float(avg_sleep),
        "as_of_kst": kst_now.strftime("%Y-%m-%d %H:%M"),
    }


def search_food_by_name(query: str, limit: int = 5) -> dict:
    """사용자 발화에서 추출한 음식명으로 food_id 후보를 검색한다.

    자유 발화 모드 전용. "짬뽕 먹어도 돼?" → search_food_by_name("짬뽕") → 첫 후보의 food_id 를
    predict_glucose_for_food 에 전달. 정확 일치 우선, 없으면 부분 일치 fallback.
    """
    if not query or not query.strip():
        return {"candidates": []}
    raw = _be_get("/api/agent/foods/search", params={"query": query.strip(), "limit": limit})
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


def predict_glucose_for_food(food_id: int) -> dict:
    """food_id 로 혈당 예측을 받아온다 (peak_mgdl / peak_minute / delta / risk_level).

    자유 발화 모드에서 사용자가 특정 음식을 먹어도 되는지 물을 때 호출한다. 응답 톤 결정에 쓴다:
    - risk_level == "high" (peak ≥ 200): 권유 X, "다른 거 어때요" 톤
    - risk_level == "elevated" (180 ≤ peak < 200): 양 조절 / 한 시간 뒤 권고
    - risk_level == "normal" (peak < 180): "괜찮아요" 톤, 양만 짚어줌
    """
    user_id = _ctx_user_id()
    if user_id is None or food_id is None:
        return {"error": "missing_user_or_food"}
    raw = _be_get(f"/api/agent/foods/{food_id}/predict-glucose", params={"userId": user_id})
    if raw is None:
        return {"error": "be_unavailable"}
    return {
        "food_id": raw.get("foodId"),
        "food_name": raw.get("foodName"),
        "current_mg_dl": raw.get("currentMgdl"),
        "peak_mg_dl": raw.get("peakMgdl"),
        "peak_minute": raw.get("peakMinute"),
        "delta_mg_dl": raw.get("deltaMgdl"),
        "risk_level": raw.get("riskLevel"),
    }


# ── 행동 도구 ─────────────────────────────────────────────


def send_command_response(
    message: str, display_trace: dict | None = None, payload: dict | None = None
) -> dict:
    """사용자 command에 대한 agent 응답 발송. parent_id로 user 메시지를 참조한다.

    options는 사용하지 않는다 (텍스트 응답 + 선택적 payload 구조화 카드만).
    BE POST /api/agent/notifications with parentChatMessageId.
    """
    user_id = _ctx_user_id()
    parent_id = _ctx_parent_chat_message_id()
    alert_type = _ctx_alert_type()
    backend_url = os.getenv("BACKEND_API_URL", "")
    agent_api_key = os.getenv("AGENT_API_KEY", "dev-agent-key-change-in-prod")

    if display_trace is None:
        display_trace = {}
    elif not isinstance(display_trace, dict):
        display_trace = {}

    display_trace.setdefault("summary", "")
    display_trace.setdefault("cards", [])
    display_trace.setdefault("decision", {"reason": ""})
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
        log.info(
            "[FoodRecommend send_command_response] status=%s body=%s",
            resp.status_code,
            resp.text[:300],
        )
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
        "name": "get_today_activity",
        "description": (
            "오늘 누적 걸음수 + 어젯밤 수면(분) + 최근 7일 평균 수면(분). 삼성헬스 동기화 값. "
            "데이터 없으면 0 반환. 음식 가부/대안 판단의 보정 신호로 사용 — "
            "걸음수 많거나 수면 충분하면 조건부 허용(식후 산책 조건), 둘 다 부족하면 양 축소/대안 권고."
        ),
        "input_schema": {"type": "object", "properties": {}},
    },
    {
        "name": "get_unseen_food_candidates",
        "description": "사용자가 안 먹어본 음식 후보를 foods 테이블에서 가져온다. search_count 인기순. 신규 음식 추천에만 사용 (items[].food_id를 채우기 위해 필수).",
        "input_schema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "default": 5, "description": "최대 N개 (1~50)"}
            },
        },
    },
    {
        "name": "search_food_by_name",
        "description": (
            "자유 발화 모드 전용 — 사용자가 발화한 음식명(예: '짬뽕')으로 food_id 후보 검색. "
            "정확 일치 우선, 없으면 부분 일치 fallback. candidates[0].food_id 를 predict_glucose_for_food 에 넘긴다."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "음식 이름. 사용자 발화에서 추출."},
                "limit": {"type": "integer", "default": 5, "description": "최대 후보 수 (1~10)"},
            },
            "required": ["query"],
        },
    },
    {
        "name": "predict_glucose_for_food",
        "description": (
            "자유 발화 모드 전용 — food_id 로 혈당 예측. 응답에 peak_mg_dl / peak_minute / delta_mg_dl / risk_level 포함. "
            "risk_level: 'high'(>=200), 'elevated'(>=180), 'normal'(<180), 'unknown'. "
            "이 결과로 응답 톤 결정: high→권유 X / elevated→양 조절·시간 권고 / normal→가볍게 OK."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "food_id": {
                    "type": "integer",
                    "description": "search_food_by_name 결과의 food_id 또는 user_food_grades 의 foodId.",
                }
            },
            "required": ["food_id"],
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
                    "description": (
                        "추론 메타. summary 1줄 + cards 배열 + decision.reason. "
                        "FE의 '키키가 확인한 내용 보기' 카드가 cards[] 와 decision.reason 을 펼쳐 보여준다."
                    ),
                    "properties": {
                        "summary": {"type": "string", "description": "어떤 기준으로 골랐는지 1줄"},
                        "cards": {
                            "type": "array",
                            "description": (
                                "확인한 신호 카드 (사용자 노출). 호출한 도구 결과 중 의미 있는 것만 1~4개. "
                                "수치 데이터가 0이거나 도구 호출 실패면 해당 카드 생략."
                            ),
                            "items": {
                                "type": "object",
                                "properties": {
                                    "type": {
                                        "type": "string",
                                        "description": "신호 종류. FE 아이콘 매핑: glucose/meal/activity/sleep 중 하나 권장. 그 외는 일반 아이콘.",
                                    },
                                    "title": {"type": "string", "description": "카드 제목 (예: '최근 혈당', '오늘 걸음수')"},
                                    "description": {
                                        "type": "string",
                                        "description": "카드 본문. 구체적인 수치 포함 (예: '125 mg/dL, 30분 전 식사 후', '오늘 8,200보 — 활동량 충분')",
                                    },
                                },
                                "required": ["type", "title", "description"],
                            },
                        },
                        "decision": {
                            "type": "object",
                            "description": "이 응답을 고른 최종 근거 1~2줄. 사용자 노출 가능 톤.",
                            "properties": {
                                "reason": {"type": "string"},
                            },
                            "required": ["reason"],
                        },
                    },
                    "required": ["summary", "cards", "decision"],
                },
                "payload": {
                    "type": "object",
                    "description": "구조화된 응답 콘텐츠. 일반 추천 시 items, A/B 비교 시 comparison 사용.",
                    "properties": {
                        "comparison": {
                            "type": "object",
                            "description": "A/B 음식 비교 결과. 비교 질문 응답 시에만 채운다. 일반 추천 시 생략.",
                            "properties": {
                                "food_a": {
                                    "type": "object",
                                    "properties": {
                                        "name": {"type": "string"},
                                        "peak_mg_dl": {"type": "number", "description": "예측 최고 혈당 (mg/dL)"},
                                        "risk_level": {"type": "string", "description": "normal / elevated / high / unknown"},
                                    },
                                    "required": ["name", "peak_mg_dl", "risk_level"],
                                },
                                "food_b": {
                                    "type": "object",
                                    "properties": {
                                        "name": {"type": "string"},
                                        "peak_mg_dl": {"type": "number"},
                                        "risk_level": {"type": "string"},
                                    },
                                    "required": ["name", "peak_mg_dl", "risk_level"],
                                },
                                "winner": {
                                    "type": "string",
                                    "description": "'food_a', 'food_b', 'tie' 중 하나.",
                                },
                            },
                            "required": ["food_a", "food_b", "winner"],
                        },
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
            "required": ["message"],
        },
    },
]


FOOD_RECOMMEND_TOOL_MAP = {
    "get_user_food_grades": get_user_food_grades,
    "get_recent_meals": get_recent_meals,
    "get_user_profile": get_user_profile,
    "get_glucose_recent": get_glucose_recent,
    "get_today_activity": get_today_activity,
    "get_unseen_food_candidates": get_unseen_food_candidates,
    "search_food_by_name": search_food_by_name,
    "predict_glucose_for_food": predict_glucose_for_food,
    "send_command_response": send_command_response,
}
