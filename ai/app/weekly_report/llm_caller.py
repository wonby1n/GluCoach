"""주간 보고서 LLM 단일 호출 모듈 (Claude Haiku).

agentic loop 없이 한 번의 messages.create()로
ai_summary, ai_suggest JSON을 생성한다.
"""

import json
import logging
import os
import re
import time

import anthropic

from app.schemas.report import WeeklyReportRequest
from app.weekly_report.prompt import build_weekly_report_prompt

log = logging.getLogger(__name__)

MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 2048
MAX_ATTEMPTS = 3
RETRY_DELAYS = [1, 2, 4]
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 529}


def _call_with_retry(client: anthropic.Anthropic, system_prompt: str) -> str:
    """Claude Haiku를 호출하고 실패 시 재시도한다. 최종 실패 시 RuntimeError 발생."""
    last_error: Exception | None = None

    for attempt in range(MAX_ATTEMPTS):
        try:
            response = client.messages.create(
                model=MODEL,
                max_tokens=MAX_TOKENS,
                system=system_prompt,
                messages=[
                    {"role": "user", "content": "주간 보고서를 생성해주세요."},
                ],
            )
            if not response.content:
                raise RuntimeError("Claude Haiku 빈 응답 (content 없음)")
            return response.content[0].text

        except anthropic.APIStatusError as e:
            last_error = e
            if e.status_code not in RETRYABLE_STATUS_CODES:
                log.error("재시도 불가 에러 (status=%d): %s", e.status_code, e)
                break
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 실패 (status=%d), %ds 후 재시도",
                            attempt + 1, MAX_ATTEMPTS, e.status_code, delay)
                time.sleep(delay)

        except (anthropic.APIConnectionError, anthropic.APITimeoutError) as e:
            last_error = e
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 네트워크 에러, %ds 후 재시도: %s",
                            attempt + 1, MAX_ATTEMPTS, delay, e)
                time.sleep(delay)

    raise RuntimeError(f"Claude Haiku 호출 실패 (3회 시도): {last_error}")


def _parse_json_response(text: str) -> dict:
    """LLM 응답에서 JSON을 추출·파싱한다."""
    match = re.search(r"```json\s*(.*?)\s*```", text, re.DOTALL)
    if match:
        return json.loads(match.group(1))

    match = re.search(r"\{.*\}", text, re.DOTALL)
    if match:
        return json.loads(match.group())

    raise ValueError(f"JSON을 찾을 수 없음: {text[:200]}")


def call_weekly_report_llm(req: WeeklyReportRequest) -> tuple[str, str]:
    """
    집계 데이터를 Claude Haiku에 주입하고 (ai_summary, ai_suggest)를 반환한다.

    Returns:
        (ai_summary, ai_suggest) 튜플

    Raises:
        RuntimeError: ANTHROPIC_API_KEY 미설정, LLM 호출 실패, JSON 파싱 실패
    """
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY가 설정되지 않음")

    client = anthropic.Anthropic(api_key=api_key)
    system_prompt = build_weekly_report_prompt(req)

    raw_text = _call_with_retry(client, system_prompt)
    log.debug("LLM 응답 원문: %s", raw_text[:300])

    data = _parse_json_response(raw_text)
    summary = data.get("ai_summary", "").strip()
    suggest = data.get("ai_suggest", "").strip()

    if not summary or not suggest:
        raise ValueError("ai_summary 또는 ai_suggest 필드가 비어 있음")

    return summary, suggest
