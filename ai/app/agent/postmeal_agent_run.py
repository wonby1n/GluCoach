"""
Glucoach Agent — 식후 활동 유도 agent
======================================
식사 기록 후 60분에 트리거되어 혈당 흐름·활동량을 확인하고
가벼운 활동을 권유하는 알림 1개를 발송한다.

실행 방법:
    cd ai
    python -m app.agent.postmeal_agent_run
"""

import json
import sys
import os
import time

from dotenv import load_dotenv
import anthropic

from app.agent.tools import TOOL_SCHEMAS, TOOL_MAP, set_agent_context
from app.agent.prompts import build_postmeal_prompt
from app.agent.trace_writer import save_trace
from app.agent.fallback import call_llm_with_retry, get_fallback_message

# ── 환경 설정 ─────────────────────────────────────────────

load_dotenv()

# Windows 한글 출력
sys.stdout.reconfigure(encoding="utf-8")

# GMS 프록시 주소
BASE_URL = "https://api.anthropic.com"
MODEL = "claude-sonnet-4-6"
# 푸시 본문은 짧지만 tool_use JSON 자체(display_trace 의 cards 등)가 한도에 잡혀
# 끝부분 필드가 절단되는 사례가 있어 2048 로 상향. 응답 시간 영향은 미미.
MAX_TOKENS = 2048
MAX_TURNS = 10


# ── 도구 실행 ─────────────────────────────────────────────

def execute_tool(name: str, tool_input: dict) -> str:
    """TOOL_MAP에서 함수를 찾아 실행하고 JSON 문자열로 반환한다.

    LLM 이 required 인자를 빠뜨려도 background task 가 죽지 않도록
    TypeError / 일반 예외를 tool_result 로 돌려보내 LLM 이 재시도할 수 있게 한다.
    """
    func = TOOL_MAP.get(name)
    if not func:
        return json.dumps({"error": f"Unknown tool: {name}"}, ensure_ascii=False)

    try:
        result = func(**tool_input)
    except TypeError as e:
        return json.dumps(
            {"status": "error", "error": f"invalid_arguments: {e}", "tool": name},
            ensure_ascii=False,
        )
    except Exception as e:
        return json.dumps(
            {"status": "error", "error": f"{type(e).__name__}: {e}", "tool": name},
            ensure_ascii=False,
        )
    return json.dumps(result, ensure_ascii=False)


# ── Agentic Loop ──────────────────────────────────────────

def run_postmeal_agent(trigger: dict, user_id: int = None, alert_type: str = "AGENT_MEAL_FOLLOWUP"):
    """
    trigger: agent를 깨운 이유와 컨텍스트
        - reason   : "meal_recorded" | "schedule_followup" | "user_response"
        - meal_time: 식사 시각 (예: "2026-05-04 12:00")
        - user_reply: 사용자 응답 텍스트 (재트리거 시)
    """
    is_test = os.getenv("AGENT_TEST_MODE", "false").lower() == "true"
    if is_test:
        alert_type = f"{alert_type}_{int(time.time())}"
    set_agent_context(user_id=user_id, alert_type=alert_type)
    client = anthropic.Anthropic(
        api_key=os.getenv("ANTHROPIC_API_KEY"),
        base_url=BASE_URL,
    )

    messages = [
        {"role": "user", "content": "식후 활동 체크해줘."}
    ]

    turn = 0
    sent_message = None
    tool_call_details = []
    scheduled_followup = None

    while turn < MAX_TURNS:
        turn += 1
        print(f"\n{'='*60}")
        print(f" Turn {turn}: Claude API 호출")
        print(f"{'='*60}")

        response = call_llm_with_retry(
            client,
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=build_postmeal_prompt(trigger),
            tools=TOOL_SCHEMAS,
            messages=messages,
        )

        # ── LLM 호출 실패 → 폴백 ────────────────────────
        if response is None:
            fallback_msg = get_fallback_message("postmeal")
            print(f"\n[fallback] LLM 호출 실패 → 폴백 메시지: {fallback_msg}")
            execute_tool("send_notification", {"message": fallback_msg, "options": [], "display_trace": {}})
            return {
                "message":           fallback_msg,
                "turns":             turn,
                "tool_call_details": tool_call_details,
                "messages":          messages,
                "scheduled_followup": scheduled_followup,
                "trigger":           trigger,
                "error":             "llm_call_failed",
            }

        print(f"  stop_reason: {response.stop_reason}")

        # ── 응답 처리 ──────────────────────────────────────
        tool_use_blocks = []

        for block in response.content:
            if block.type == "text":
                print(f"\n[Claude 응답]\n{block.text}")
            elif block.type == "tool_use":
                tool_use_blocks.append(block)
                print(f"\n[도구 호출] {block.name}")
                print(f"  params: {json.dumps(block.input, ensure_ascii=False)}")

        # ── 종료 조건 ──────────────────────────────────────
        if response.stop_reason == "end_turn":
            print(f"\n{'='*60}")
            print(" Agent 완료!")
            print(f"{'='*60}")
            break

        # ── 도구 실행 → 결과를 다음 메시지로 전달 ──────────
        messages.append({"role": "assistant", "content": response.content})

        tool_results = []
        for block in tool_use_blocks:
            result = execute_tool(block.name, block.input)
            print(f"  -> {block.name} 결과: {result[:100]}...")

            tool_call_details.append({
                "name": block.name,
                "input": block.input,
                "result": json.loads(result),
            })

            if block.name == "send_notification":
                sent_message = block.input.get("message")

            if block.name == "schedule_followup":
                scheduled_followup = block.input

            tool_results.append({
                "type": "tool_result",
                "tool_use_id": block.id,
                "content": result,
            })

        messages.append({"role": "user", "content": tool_results})

    # ── 턴 초과 안전장치 ──────────────────────────────────
    if turn >= MAX_TURNS:
        print(f"\n{'='*60}")
        print(f" 최대 턴({MAX_TURNS}) 초과. 종료.")
        print(f"{'='*60}")

    # ── 토큰 사용량 ───────────────────────────────────────
    print(f"\n[토큰 사용량] input={response.usage.input_tokens}, output={response.usage.output_tokens}")

    return {
        "message":           sent_message,
        "turns":             turn,
        "tool_call_details": tool_call_details,
        "messages":          messages,
        "scheduled_followup": scheduled_followup,
        "trigger":           trigger,
    }


# ── 실행 ──────────────────────────────────────────────────

DEMO_FOLLOWUP_DELAY_SECONDS = 5  # 시연용: 실제 30분 대신 5초


if __name__ == "__main__":
    import time

    # ── 817: 식사 기록 → 첫 활동 알림 ──────────────────────
    print("\n" + "▶" * 30)
    print(" [817] meal_recorded → 첫 활동 알림")
    print("▶" * 30)

    trigger_817 = {
        "reason":    "meal_recorded",
        "meal_time": "2026-05-04 12:00",
    }

    result_817 = run_postmeal_agent(trigger_817, user_id=5, alert_type="AGENT_MEAL_FOLLOWUP")
    print(f"\n[817 결과] message={result_817['message']}")
    save_trace(result_817, agent_type="postmeal")

    # ── 818: 사용자 응답 선택 → 응답 처리 ───────────────────
    print("\n" + "-" * 40)
    print(" 사용자 응답을 선택하세요:")
    print("  1) 알겠어요.  (수락)")
    print("  2) 지금 회의 중이에요.  (지금 불가)")
    print("  3) 괜찮아요.  (거절)")
    print("-" * 40)

    choice = input("선택 (1/2/3): ").strip()
    reply_map = {"1": "알겠어요.", "2": "지금 회의 중이에요.", "3": "괜찮아요."}
    user_reply = reply_map.get(choice, "지금 회의 중이에요.")

    print(f"\n→ 사용자 응답: \"{user_reply}\"")

    print("\n" + "▶" * 30)
    print(f" [818] user_response → 사용자 응답 처리")
    print("▶" * 30)

    trigger_818 = {
        "reason":                        "user_response",
        "meal_time":                     "2026-05-04 12:00",
        "previous_notification_sent_at": "2026-05-04 13:00",
        "user_reply":                    user_reply,
    }

    result_818 = run_postmeal_agent(trigger_818, user_id=5, alert_type="AGENT_MEAL_REPLY")
    print(f"\n[818 결과] message={result_818['message']}")
    print(f"[818 followup] {result_818['scheduled_followup']}")
    save_trace(result_818, agent_type="postmeal_reply")

    # ── 819: schedule_followup 감지 → 자동 재시도 ──────────
    if result_818["scheduled_followup"]:
        delay_min = result_818["scheduled_followup"]["delay_minutes"]
        print(f"\n[{delay_min}분 후 재시도 예약됨 → {DEMO_FOLLOWUP_DELAY_SECONDS}초 후 자동 실행]")
        time.sleep(DEMO_FOLLOWUP_DELAY_SECONDS)

        print("\n" + "▶" * 30)
        print(" [819] schedule_followup → 자동 재시도")
        print("▶" * 30)

        trigger_819 = {
            "reason":         "schedule_followup",
            "meal_time":      trigger_818["meal_time"],
            "original_reply": trigger_818["user_reply"],
            "followup_at":    "2026-05-04 13:30",
        }

        result_819 = run_postmeal_agent(trigger_819, user_id=5, alert_type="AGENT_MEAL_RETRY")
        print(f"\n[819 결과] message={result_819['message']}")
        save_trace(result_819, agent_type="postmeal_followup")
