"""주간 보고서 LLM 단일 호출 모듈 (GPT-4o).

agentic loop 없이 한 번의 chat.completions.create()로
ai_summary, ai_suggest JSON을 생성한다.
"""

import json
import logging
import re
import time

from openai import OpenAI, APIStatusError, APIConnectionError, APITimeoutError

from app.core.config import settings
from app.schemas.report import WeeklyReportRequest
from app.report.prompt import build_weekly_report_prompt

log = logging.getLogger(__name__)

MODEL = "gpt-4o"
MAX_TOKENS = 2048
MAX_ATTEMPTS = 3
RETRY_DELAYS = [1, 2, 4]
RETRYABLE_STATUS_CODES = {429, 500, 502, 503}


def _call_with_retry(client: OpenAI, system_prompt: str) -> str:
    """GPT-4o를 호출하고 실패 시 재시도한다. 최종 실패 시 RuntimeError 발생."""
    last_error: Exception | None = None

    for attempt in range(MAX_ATTEMPTS):
        try:
            response = client.chat.completions.create(
                model=MODEL,
                max_tokens=MAX_TOKENS,
                temperature=0.7,
                timeout=60.0,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": "주간 보고서를 생성해주세요."},
                ],
            )
            if not response.choices:
                raise RuntimeError("GPT-4o 빈 응답 (choices 없음)")
            return response.choices[0].message.content or ""

        except APIStatusError as e:
            last_error = e
            if e.status_code not in RETRYABLE_STATUS_CODES:
                log.error("재시도 불가 에러 (status=%d): %s", e.status_code, e)
                break
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 실패 (status=%d), %ds 후 재시도",
                            attempt + 1, MAX_ATTEMPTS, e.status_code, delay)
                time.sleep(delay)

        except (APIConnectionError, APITimeoutError) as e:
            last_error = e
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 네트워크 에러, %ds 후 재시도: %s",
                            attempt + 1, MAX_ATTEMPTS, delay, e)
                time.sleep(delay)

    raise RuntimeError(f"GPT-4o 호출 실패 (3회 시도): {last_error}")


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
    집계 데이터를 GPT-4o에 주입하고 (ai_summary, ai_suggest)를 반환한다.

    Returns:
        (ai_summary, ai_suggest) 튜플

    Raises:
        RuntimeError: OPENAI_API_KEY 미설정, LLM 호출 실패, JSON 파싱 실패
    """
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않음")

    client = OpenAI(
        api_key=settings.openai_api_key,
        base_url="https://gms.ssafy.io/gmsapi/api.openai.com/v1",
    )
    system_prompt = build_weekly_report_prompt(req)

    raw_text = _call_with_retry(client, system_prompt)
    log.debug("LLM 응답 원문: %s", raw_text[:300])

    data = _parse_json_response(raw_text)
    summary = data.get("ai_summary", "").strip()
    suggest = data.get("ai_suggest", "").strip()

    if not summary or not suggest:
        raise ValueError("ai_summary 또는 ai_suggest 필드가 비어 있음")

    return summary, suggest
