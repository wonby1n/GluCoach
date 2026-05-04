"""
Glucoach Agent — Tool-calling Hello World
==========================================
Claude API에 시스템 프롬프트 + TOOL_SCHEMAS를 전달하고,
Claude가 도구를 호출하면 TOOL_MAP으로 실행 → 결과를 다시 보내는
agentic loop의 최소 구현.

실행 방법:
    cd ai
    python -m app.agent.agent_run
"""

import json
import sys
import os

from dotenv import load_dotenv
import anthropic

from app.agent.tools import TOOL_SCHEMAS, TOOL_MAP
from app.agent.mock_data import USER_INFO, DEMO_DATE

# ── 환경 설정 ─────────────────────────────────────────────

load_dotenv()

# Windows 한글 출력
sys.stdout.reconfigure(encoding="utf-8")

# GMS 프록시 주소
BASE_URL = "https://gms.ssafy.io/gmsapi/api.anthropic.com"
MODEL = "claude-sonnet-4-5-20250929"
MAX_TOKENS = 4096
MAX_TURNS = 10


# ── 시스템 프롬프트 ───────────────────────────────────────

SYSTEM_PROMPT = f"""당신은 당뇨 환자의 혈당 관리를 돕는 AI 코치입니다.

[사용자 정보]
- 이름: {USER_INFO["name"]}
- 직업: {USER_INFO["job"]}
- 당뇨 유형: {USER_INFO["diabetes_type"]}형
- 식전 목표 혈당: 80~130 mg/dL
- 식후 2시간 목표: 180 mg/dL 미만
- 저혈당 주의: 70 mg/dL 미만
- 고혈당 주의: 180 mg/dL 초과

지금은 아침이고, 사용자의 어젯밤 데이터를 보고 오늘의 혈당 관리 전략을
알려줘야 해. 다음 도구들을 사용해서 상황을 파악한 뒤, 적절한 알림을 보내.

도구 목록: [get_sleep, get_glucose, get_meals,
            get_notification_history, send_notification]

판단 기준:
- 어젯밤 수면이 평소보다 부족함
- 어제 저녁 식후 최고 혈당이 평소보다 높았음
- 어제 혈당 변동폭이 평소보다 큼

고려할 점:
- 수면, 혈당, 식사 데이터를 종합해서 메시지 작성
- 알림 이력을 확인해서 중복 알림 방지
- 오늘 날짜는 {DEMO_DATE['today']}, 어제 날짜는 {DEMO_DATE['yesterday']}

모든 결정 과정은 reasoning에 남겨."""


# ── 도구 실행 ─────────────────────────────────────────────

def execute_tool(name: str, tool_input: dict) -> str:
    """TOOL_MAP에서 함수를 찾아 실행하고 JSON 문자열로 반환한다."""
    func = TOOL_MAP.get(name)
    if not func:
        return json.dumps({"error": f"Unknown tool: {name}"}, ensure_ascii=False)

    result = func(**tool_input)
    return json.dumps(result, ensure_ascii=False)


# ── Agentic Loop ──────────────────────────────────────────

def run_agent():
    """Claude API를 호출하고, 도구 호출이 끝날 때까지 루프를 돈다."""
    client = anthropic.Anthropic(
        api_key=os.getenv("ANTHROPIC_API_KEY"),
        base_url=BASE_URL,
    )

    messages = [
        {"role": "user", "content": "오늘 아침 혈당 관리 브리핑 해줘."}
    ]

    turn = 0

    while turn < MAX_TURNS:
        turn += 1
        print(f"\n{'='*60}")
        print(f" Turn {turn}: Claude API 호출")
        print(f"{'='*60}")

        response = client.messages.create(
            model=MODEL,
            max_tokens=MAX_TOKENS,
            system=SYSTEM_PROMPT,
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


# ── 실행 ──────────────────────────────────────────────────

if __name__ == "__main__":
    run_agent()
