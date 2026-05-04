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


def save_trace(agent_result: dict, agent_type: str = "morning") -> str:
    """
    agent_result: run_agent()의 반환값
    agent_type  : 파일명 prefix (morning / postmeal)
    반환값      : 저장된 파일 경로
    """
    os.makedirs(TRACE_DIR, exist_ok=True)

    filename = f"{agent_type}_latest.json"
    filepath = os.path.join(TRACE_DIR, filename)

    trace = {
        "agent":        agent_type,
        "triggered_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "message":      agent_result.get("message"),
        "turns":        agent_result.get("turns"),
        "tool_calls":   agent_result.get("tool_call_details", []),
    }

    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(trace, f, ensure_ascii=False, indent=2)

    return filepath
