"""
Reasoning Trace 저장 모듈
=========================
run_agent() 반환값에서 tool_call_details를 추출해
JSON 파일로 저장한다.

저장 위치: ai/traces/morning_latest.json  (항상 덮어쓰기)
"""

import json
import os
from datetime import datetime

TRACE_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "traces")


def _get_tool_result(tool_calls: list[dict], tool_name: str) -> dict:
    """tool_calls에서 특정 도구의 result를 꺼낸다."""
    for call in tool_calls:
        if call.get("name") == tool_name:
            return call.get("result", {})
    return {}


def build_morning_display_trace(tool_calls: list[dict], final_message: str) -> dict:
    """
    FE의 '왜 이 알림?' 화면에 보여줄 요약 trace를 만든다.

    - display_trace: 사용자 화면용 요약 데이터
    - tool_calls: 개발자 디버깅용 원본 도구 호출 로그
    - FE는 display_trace.summary, display_trace.cards,
      display_trace.decision.reason을 중심으로 사용한다.
    """
    sleep_result = _get_tool_result(tool_calls, "get_sleep")
    glucose_result = _get_tool_result(tool_calls, "get_glucose")
    meals_result = _get_tool_result(tool_calls, "get_meals")
    notification_result = _get_tool_result(tool_calls, "get_notification_history")

    cards = []

    # 1. 수면 카드
    sleep = sleep_result.get("sleep", {})
    average_7d = sleep_result.get("average_7d", {})

    sleep_hours = sleep.get("duration_hours")
    avg_sleep_hours = average_7d.get("duration_hours")

    if sleep_hours is not None and avg_sleep_hours is not None:
        sleep_gap = avg_sleep_hours - sleep_hours

        cards.append({
            "type": "sleep",
            "title": "수면 시간",
            "description": f"어젯밤 수면은 {sleep_hours:g}시간으로, 평소보다 {sleep_gap:g}시간 부족했어요.",
            "severity": "caution"
        })

    # 2. 혈당 카드
    readings = glucose_result.get("readings", [])
    values = [
        item.get("glucose_mg_dl")
        for item in readings
        if item.get("glucose_mg_dl") is not None
    ]

    if values:
        max_glucose = max(values)
        min_glucose = min(values)
        variation = max_glucose - min_glucose

        cards.append({
            "type": "glucose",
            "title": "혈당 흐름",
            "description": f"전날 밤~기상 전 최고 {max_glucose}mg/dL, 최저 {min_glucose}mg/dL로 변화폭이 {variation}mg/dL였어요.",
            "severity": "caution"
        })

    # 3. 식사 카드
    meals = meals_result.get("meals", [])

    if meals:
        dinner = meals[0]
        foods = dinner.get("foods", [])
        carbs_g = dinner.get("carbs_g")

        foods_text = " + ".join(foods) if foods else "식사 기록"

        description = f"전날 마지막 식사는 {foods_text}였어요."
        if carbs_g is not None:
            description += f" 탄수화물은 {carbs_g}g으로 기록됐어요."

        cards.append({
            "type": "meal",
            "title": "식사 기록",
            "description": description,
            "severity": "info"
        })

    # 4. 중복 알림 확인
    notifications = notification_result.get("notifications", [])
    duplicate_found = False

    # mock 기준: 오늘 날짜가 2026-05-04이고, 오늘 아침 전략 알림이 있으면 중복
    for notif in notifications:
        sent_at = notif.get("sent_at", "")
        notif_type = notif.get("type", "")
        if sent_at.startswith("2026-05-04") and notif_type == "오늘의 혈당 전략":
            duplicate_found = True
            break

    notification_check = {
        "checked": bool(notification_result),
        "duplicate_found": duplicate_found,
        "description": (
            "오늘 아침에는 같은 유형의 알림이 이미 발송되어 있었어요."
            if duplicate_found
            else "오늘 아침에는 같은 유형의 알림이 아직 발송되지 않았어요."
        )
    }

    return {
        "title": "키키가 확인한 내용",
        "summary": "어젯밤 수면, 혈당 흐름, 전날 식사 기록을 함께 확인해 오늘 아침 전략을 정했어요.",
        "cards": cards,
        "notification_check": notification_check,
        "decision": {
            "action": "send_notification",
            "reason": "여러 신호 중 사용자가 가장 부담 없이 이해할 수 있는 수면 부족을 중심으로 메시지를 구성했어요.",
            "message_rule": "수치와 경고 표현은 줄이고, 오늘 아침 행동 제안 1개만 담았어요."
        },
        "notification_message": final_message
    }


def build_postmeal_display_trace(tool_calls: list[dict], final_message: str) -> dict:
    """
    식후 에이전트용 display_trace를 만든다.
    postmeal / postmeal_reply / postmeal_followup 공용.

    FE는 display_trace.summary, display_trace.cards,
    display_trace.decision.reason을 중심으로 사용한다.
    """
    meals_result = _get_tool_result(tool_calls, "get_meals")
    glucose_result = _get_tool_result(tool_calls, "get_glucose")
    steps_result = _get_tool_result(tool_calls, "get_steps")

    cards = []

    # 1. 식사 카드
    meals = meals_result.get("meals", [])
    if meals:
        meal = meals[-1]  # 가장 최근 식사
        foods = meal.get("foods", [])
        meal_time = meal.get("time", "")
        carbs_g = meal.get("carbs_g")

        foods_text = " + ".join(foods) if foods else "식사 기록"
        time_short = meal_time.split(" ")[-1] if " " in meal_time else meal_time

        description = f"{time_short}에 {foods_text}을(를) 드셨어요."
        if carbs_g is not None:
            description += f" 탄수화물 {carbs_g}g."

        cards.append({
            "type": "meal",
            "title": "식사 기록",
            "description": description,
            "severity": "info"
        })

    # 2. 혈당 흐름 카드
    readings = glucose_result.get("readings", [])
    values = [
        item.get("glucose_mg_dl")
        for item in readings
        if item.get("glucose_mg_dl") is not None
    ]

    if values:
        first_val = values[0]
        peak_val = max(values)
        rise = peak_val - first_val

        flow_text = " → ".join(f"{v}" for v in values)

        severity = "caution" if rise >= 40 else "info"
        title = "식후 혈당 흐름"

        cards.append({
            "type": "glucose",
            "title": title,
            "description": f"{flow_text} mg/dL (식후 +{rise}mg/dL)",
            "severity": severity
        })

    # 3. 활동량 카드
    total_steps = steps_result.get("total_steps")
    if total_steps is not None:
        severity = "caution" if total_steps < 100 else "good"
        title = "식후 활동량"

        cards.append({
            "type": "activity",
            "title": title,
            "description": f"식후 걸음수 {total_steps}보",
            "severity": severity
        })

    # decision reason 결정
    has_high_rise = len(values) > 0 and (max(values) - values[0]) >= 40
    has_low_activity = total_steps is not None and total_steps < 100

    if has_high_rise and has_low_activity:
        reason = "혈당 상승과 활동 부족이 함께 감지되어 가벼운 활동을 제안했어요."
    elif has_high_rise:
        reason = "식후 혈당이 많이 올라서 활동을 제안했어요."
    elif has_low_activity:
        reason = "식후 움직임이 적어서 가벼운 활동을 제안했어요."
    else:
        reason = "식후 컨디션을 확인하고 메시지를 보냈어요."

    return {
        "title": "키키가 확인한 내용",
        "summary": "식사 기록, 식후 혈당 흐름, 활동량을 함께 확인해 활동을 제안했어요.",
        "cards": cards,
        "decision": {
            "action": "send_notification",
            "reason": reason,
            "message_rule": "부담 없는 톤으로 활동 1가지를 제안했어요."
        },
        "notification_message": final_message
    }


def save_trace(agent_result: dict, agent_type: str = "morning") -> str:
    """
    agent_result: run_agent()의 반환값
    agent_type  : 파일명 prefix (morning / postmeal)
    반환값      : 저장된 파일 경로
    """
    os.makedirs(TRACE_DIR, exist_ok=True)

    filename = f"{agent_type}_latest.json"
    filepath = os.path.join(TRACE_DIR, filename)

    tool_calls = agent_result.get("tool_call_details", [])
    message = agent_result.get("message")

    display_trace = None
    if agent_type == "morning":
        display_trace = build_morning_display_trace(tool_calls, message)
    elif agent_type in ("postmeal", "postmeal_reply", "postmeal_followup"):
        display_trace = build_postmeal_display_trace(tool_calls, message)
    elif agent_type == "food_recommend":
        # food_recommend agent 는 send_command_response 호출에 display_trace 를 직접 채워 보낸다.
        # tool_call_details 에서 해당 호출의 input.display_trace 를 그대로 가져오면 FE 가 본 것과 동일.
        for call in tool_calls:
            if call.get("name") == "send_command_response":
                display_trace = (call.get("input") or {}).get("display_trace")
                break

    trace = {
        "agent":        agent_type,
        "triggered_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "message":      message,
        "display_trace": display_trace,
        "turns":        agent_result.get("turns"),
        "tool_calls":   tool_calls,
    }

    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(trace, f, ensure_ascii=False, indent=2)

    return filepath