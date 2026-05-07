"""
Glucoach Agent — 오늘의 혈당 전략 agent
========================================
Claude API에 시스템 프롬프트 + TOOL_SCHEMAS를 전달하고,
Claude가 도구를 호출하면 TOOL_MAP으로 실행 → 결과를 다시 보내는
agentic loop 구현.

실행 방법:
    cd ai
    python -m app.agent.morning_agent_run
"""

import json
import sys
import os
import time

from dotenv import load_dotenv
import anthropic

from app.agent.tools import TOOL_SCHEMAS, TOOL_MAP, set_agent_context
from app.agent.prompts import build_morning_prompt
from app.agent.trace_writer import save_trace
from app.agent.fallback import call_llm_with_retry, get_fallback_message

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

def run_agent(user_id: int = None):
    """Claude API를 호출하고, 도구 호출이 끝날 때까지 루프를 돈다."""
    is_test = os.getenv("AGENT_TEST_MODE", "false").lower() == "true"
    alert_type = f"AGENT_WAKE_UP_{int(time.time())}" if is_test else "AGENT_WAKE_UP"
    set_agent_context(user_id=user_id, alert_type=alert_type)
    client = anthropic.Anthropic(
        api_key=os.getenv("ANTHROPIC_API_KEY"),
        base_url=BASE_URL,
    )

    messages = [
        {"role": "user", "content": "오늘 아침 혈당 관리 브리핑 해줘."}
    ]

    turn = 0
    sent_message = None   # send_notification으로 보낸 메시지
    tool_call_details = []  # 도구 호출 이력 (815 reasoning trace용)

    while turn < MAX_TURNS:
        turn += 1
        print(f"\n{'='*60}")
        print(f" Turn {turn}: Claude API 호출")
        print(f"{'='*60}")

        response = call_llm_with_retry(
            client,
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=build_morning_prompt(),
            tools=TOOL_SCHEMAS,
            messages=messages,
        )

        # ── LLM 호출 실패 → 폴백 ────────────────────────
        if response is None:
            fallback_msg = get_fallback_message("morning")
            print(f"\n[fallback] LLM 호출 실패 → 폴백 메시지: {fallback_msg}")
            return {
                "message":          fallback_msg,
                "turns":            turn,
                "tool_call_details": tool_call_details,
                "messages":         messages,
                "error":            "llm_call_failed",
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
        "message":          sent_message,
        "turns":            turn,
        "tool_call_details": tool_call_details,
        "messages":         messages,
    }


# ── 실행 ──────────────────────────────────────────────────

if __name__ == "__main__":
    result = run_agent(user_id=5)
    print(f"\n[최종 결과] message={result['message']}")
    filepath = save_trace(result, agent_type="morning")
    print(f"[trace 저장] {filepath}")
