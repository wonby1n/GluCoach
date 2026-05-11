"""
음식 추천 Agent — agentic loop
==================================
사용자 command 발화 → BE webhook → 이 agent 실행 → BE INSERT(parent_id 채워서) → FCM
"""

import json
import os
import sys

from dotenv import load_dotenv
import anthropic

from app.agent.food_recommend_tools import (
    FOOD_RECOMMEND_TOOL_SCHEMAS,
    FOOD_RECOMMEND_TOOL_MAP,
    set_food_agent_context,
)
from app.agent.food_recommend_prompts import build_food_recommend_prompt
from app.agent.fallback import call_llm_with_retry, get_fallback_message

load_dotenv()

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE_URL = "https://api.anthropic.com"
MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 4096
MAX_TURNS = 10


def _execute(name: str, tool_input: dict) -> str:
    func = FOOD_RECOMMEND_TOOL_MAP.get(name)
    if not func:
        return json.dumps({"error": f"Unknown tool: {name}"}, ensure_ascii=False)
    result = func(**tool_input)
    return json.dumps(result, ensure_ascii=False)


def run_food_recommend_agent(
    user_id: int,
    parent_chat_message_id: int,
    payload: dict | None = None,
) -> dict:
    """음식 추천 agent 실행.

    user_id, parent_chat_message_id는 BE webhook에서 전달받음.
    """
    set_food_agent_context(
        user_id=user_id,
        parent_chat_message_id=parent_chat_message_id,
        alert_type="AGENT_FOOD_RECOMMEND",
    )
    client = anthropic.Anthropic(
        api_key=os.getenv("ANTHROPIC_API_KEY"),
        base_url=BASE_URL,
    )

    messages = [{"role": "user", "content": "사용자가 음식 추천을 요청했어. 절차대로 데이터 확인하고 응답해."}]
    turn = 0
    sent_message = None
    tool_call_details: list = []
    response = None

    while turn < MAX_TURNS:
        turn += 1
        response = call_llm_with_retry(
            client,
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=build_food_recommend_prompt(payload or {}),
            tools=FOOD_RECOMMEND_TOOL_SCHEMAS,
            messages=messages,
        )

        if response is None:
            return {
                "message": get_fallback_message("postmeal"),
                "turns": turn,
                "tool_call_details": tool_call_details,
                "error": "llm_call_failed",
            }

        tool_use_blocks = []
        for block in response.content:
            if block.type == "text":
                print(f"[FoodRecommend] {block.text[:200]}")
            elif block.type == "tool_use":
                tool_use_blocks.append(block)
                print(f"[FoodRecommend tool] {block.name} input={json.dumps(block.input, ensure_ascii=False)[:200]}")

        if response.stop_reason == "end_turn":
            break

        messages.append({"role": "assistant", "content": response.content})
        tool_results = []
        for block in tool_use_blocks:
            result = _execute(block.name, block.input)
            tool_call_details.append(
                {"name": block.name, "input": block.input, "result": json.loads(result)}
            )
            if block.name == "send_command_response":
                sent_message = block.input.get("message")
            tool_results.append(
                {"type": "tool_result", "tool_use_id": block.id, "content": result}
            )
        messages.append({"role": "user", "content": tool_results})

    return {
        "message": sent_message,
        "turns": turn,
        "tool_call_details": tool_call_details,
    }


if __name__ == "__main__":
    result = run_food_recommend_agent(user_id=5, parent_chat_message_id=1)
    print("\n=== 결과 ===")
    print(json.dumps(result, ensure_ascii=False, indent=2)[:2000])
