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


def _call_with_retry(client: OpenAI, system_prompt: str) -> str | None:
    """GPT-4o를 호출하고 실패 시 재시도한다. 최종 실패 시 None 반환."""
    for attempt in range(MAX_ATTEMPTS):
        try:
            response = client.chat.completions.create(
                model=MODEL,
                max_tokens=MAX_TOKENS,
                temperature=0.7,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": "주간 보고서를 생성해주세요."},
                ],
            )
            return response.choices[0].message.content or ""

        except APIStatusError as e:
            if e.status_code not in RETRYABLE_STATUS_CODES:
                log.error("재시도 불가 에러 (status=%d): %s", e.status_code, e)
                return None
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 실패 (status=%d), %ds 후 재시도",
                            attempt + 1, MAX_ATTEMPTS, e.status_code, delay)
                time.sleep(delay)
            else:
                log.error("최대 재시도 초과: %s", e)
                return None

        except (APIConnectionError, APITimeoutError) as e:
            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[attempt]
                log.warning("시도 %d/%d 네트워크 에러, %ds 후 재시도: %s",
                            attempt + 1, MAX_ATTEMPTS, delay, e)
                time.sleep(delay)
            else:
                log.error("최대 재시도 초과: %s", e)
                return None

    return None


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
        (ai_summary, ai_suggest) 튜플. 실패 시 fallback 텍스트 반환.
    """
    if not settings.openai_api_key:
        log.warning("OPENAI_API_KEY 미설정, fallback 반환")
        return _fallback_summary(req), _fallback_suggest()

    client = OpenAI(api_key=settings.openai_api_key)
    system_prompt = build_weekly_report_prompt(req)

    raw_text = _call_with_retry(client, system_prompt)

    if raw_text is None:
        log.warning("LLM 호출 최종 실패, fallback 반환")
        return _fallback_summary(req), _fallback_suggest()

    log.debug("LLM 응답 원문: %s", raw_text[:300])

    try:
        data = _parse_json_response(raw_text)
        summary = data.get("ai_summary", "").strip()
        suggest = data.get("ai_suggest", "").strip()
        if not summary or not suggest:
            raise ValueError("ai_summary 또는 ai_suggest 필드가 비어 있음")
        return summary, suggest
    except Exception as e:
        log.warning("JSON 파싱 실패 (%s), fallback 반환", e)
        return _fallback_summary(req), _fallback_suggest()


def _fallback_summary(req: WeeklyReportRequest) -> str:
    return (
        f"이번 주({req.week_start} ~ {req.week_end}) 평균 혈당은 "
        f"{req.avg_glucose:.1f} mg/dL이었으며, 목표 범위 내 시간(TIR)은 "
        f"{req.time_in_range:.1f}%였습니다. 꾸준한 관리를 이어가고 계시네요. "
        "다음 주도 함께 건강한 한 주를 만들어봐요!"
    )


def _fallback_suggest() -> str:
    return (
        "규칙적인 식사 시간을 유지하고, 식후 10~15분 가벼운 산책을 해볼까요? "
        "수면 시간도 일정하게 맞추면 혈당 안정에 도움이 됩니다. "
        "이번 주 목표: 매일 식사 시간을 비슷하게 유지해보기."
    )
