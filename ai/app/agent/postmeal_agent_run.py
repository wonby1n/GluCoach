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

from dotenv import load_dotenv
import anthropic

from app.agent.tools import TOOL_SCHEMAS, TOOL_MAP
from app.agent.prompts import build_postmeal_prompt
from app.agent.trace_writer import save_trace

# ── 환경 설정 ─────────────────────────────────────────────

load_dotenv()

# Windows 한글 출력
sys.stdout.reconfigure(encoding="utf-8")

# GMS 프록시 주소
BASE_URL = "https://gms.ssafy.io/gmsapi/api.anthropic.com"
MODEL = "claude-sonnet-4-5-20250929"
MAX_TOKENS = 4096
MAX_TURNS = 10


# ── 도구 실행 ─────────────────────────────────────────────

def execute_tool(name: str, tool_input: dict) -> str:
    """TOOL_MAP에서 함수를 찾아 실행하고 JSON 문자열로 반환한다."""
    func = TOOL_MAP.get(name)
    if not func:
        return json.dumps({"error": f"Unknown tool: {name}"}, ensure_ascii=False)

    result = func(**tool_input)
    return json.dumps(result, ensure_ascii=False)


# ── Agentic Loop ──────────────────────────────────────────

def run_postmeal_agent(trigger: dict):
    """
    trigger: agent를 깨운 이유와 컨텍스트
        - reason   : "meal_recorded" | "schedule_followup" | "user_response"
        - meal_time: 식사 시각 (예: "2026-05-04 12:00")
        - user_reply: 사용자 응답 텍스트 (재트리거 시)
    """
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

        response = client.messages.create(
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=build_postmeal_prompt(trigger),
            tools=TOOL_SCHEMAS,
            messages=messages,
        )

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

if __name__ == "__main__":
    trigger = {
        "reason":    "meal_recorded",
        "meal_time": "2026-05-04 12:00",
    }

    result = run_postmeal_agent(trigger)
    print(f"\n[최종 결과] message={result['message']}")
    print(f"[followup]  {result['scheduled_followup']}")
    filepath = save_trace(result, agent_type="postmeal")
    print(f"[trace 저장] {filepath}")
