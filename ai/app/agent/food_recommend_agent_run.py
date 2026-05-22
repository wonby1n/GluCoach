"""
음식 추천 Agent — agentic loop
==================================
사용자 command 발화 → BE webhook → 이 agent 실행 → BE INSERT(parent_id 채워서) → FCM
"""

import json
import os
import sys
import time

from dotenv import load_dotenv
import anthropic

from app.agent.food_recommend_tools import (
    FOOD_RECOMMEND_TOOL_SCHEMAS,
    FOOD_RECOMMEND_TOOL_MAP,
    set_food_agent_context,
)
from app.agent.food_recommend_prompts import build_food_recommend_prompt
from app.agent.fallback import call_llm_with_retry, get_fallback_message
from app.agent.trace_writer import save_trace

load_dotenv()

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE_URL = "https://api.anthropic.com"
MODEL = "claude-haiku-4-5-20251001"
# 음성 모달 응답은 짧으므로 1024 면 충분. 4096 대비 LLM 응답 시간 단축.
MAX_TOKENS = 2048
MAX_TURNS = 10


def _execute(name: str, tool_input: dict) -> str:
    func = FOOD_RECOMMEND_TOOL_MAP.get(name)
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

    query = (payload or {}).get("query", "").strip()
    user_content = (
        f"사용자가 음성으로 '{query}'라고 물어봤어. 자유 발화 모드로 답해."
        if query
        else "사용자가 음식 추천을 요청했어. 절차대로 데이터 확인하고 응답해."
    )
    messages = [{"role": "user", "content": user_content}]
    turn = 0
    sent_message = None
    tool_call_details: list = []
    response = None
    agent_start = time.perf_counter()

    while turn < MAX_TURNS:
        turn += 1
        llm_start = time.perf_counter()
        response = call_llm_with_retry(
            client,
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=build_food_recommend_prompt(payload or {}),
            tools=FOOD_RECOMMEND_TOOL_SCHEMAS,
            messages=messages,
        )
        print(f"[FoodRecommend] Turn {turn} Claude 호출 → {time.perf_counter() - llm_start:.2f}s")

        if response is None:
            result = {
                "message": get_fallback_message("food_recommend"),
                "turns": turn,
                "tool_call_details": tool_call_details,
                "error": "llm_call_failed",
            }
            try:
                save_trace(result, agent_type="food_recommend")
            except Exception as e:
                print(f"[FoodRecommend] trace 저장 실패 (무시): {e}")
            return result

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
        should_exit = False
        for block in tool_use_blocks:
            tool_start = time.perf_counter()
            result = _execute(block.name, block.input)
            print(f"[FoodRecommend tool] {block.name} → {time.perf_counter() - tool_start:.2f}s")
            result_dict = json.loads(result)
            tool_call_details.append(
                {"name": block.name, "input": block.input, "result": result_dict}
            )
            if block.name == "send_command_response" and result_dict.get("status") == "sent":
                sent_message = block.input.get("message")
                should_exit = True
            tool_results.append(
                {"type": "tool_result", "tool_use_id": block.id, "content": result}
            )
        # 이번 턴에 predict_glucose_for_food 를 호출했는지 확인.
        # 비교 모드(comparison)는 반드시 predict → send_command_response 순서가 필요하므로
        # predict 호출이 있었던 턴에는 "즉시 호출" 대신 다음 단계 안내로 교체.
        called_predict = any(b.name == "predict_glucose_for_food" for b in tool_use_blocks)
        next_hint = (
            "혈당 예측 완료. payload.comparison을 반드시 채워서 send_command_response를 호출하세요."
            if called_predict
            else "데이터 수집 완료. 분석 텍스트 없이 send_command_response를 즉시 호출하세요."
        )
        messages.append({"role": "user", "content": tool_results + [
            {"type": "text", "text": next_hint}
        ]})
        if should_exit:
            break

    print(f"[FoodRecommend] 전체 소요 → {time.perf_counter() - agent_start:.2f}s (turn={turn})")
    result = {
        "message": sent_message,
        "turns": turn,
        "tool_call_details": tool_call_details,
    }
    # 디버깅용 trace 저장 — ai/traces/food_recommend_latest.json. 실패해도 응답 흐름은 중단하지 않음.
    try:
        save_trace(result, agent_type="food_recommend")
    except Exception as e:
        print(f"[FoodRecommend] trace 저장 실패 (무시): {e}")
    return result


if __name__ == "__main__":
    result = run_food_recommend_agent(user_id=5, parent_chat_message_id=1)
    print("\n=== 결과 ===")
    print(json.dumps(result, ensure_ascii=False, indent=2)[:2000])
