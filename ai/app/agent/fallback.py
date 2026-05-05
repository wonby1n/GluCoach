"""
LLM 호출 실패 시 재시도 및 폴백 처리
======================================
Claude API 호출을 감싸서 일시적 에러 시 재시도하고,
모든 시도 실패 시 agent에서 폴백 메시지를 사용할 수 있도록 None을 반환한다.
"""

import time
import anthropic


# ── 재시도 설정 ──────────────────────────────────────────

MAX_ATTEMPTS = 3
RETRY_DELAYS = [1, 2, 4]  # 지수 백오프: 1초 → 2초 → 4초
TIMEOUT_SECONDS = 30  # API 호출 타임아웃

# 재시도 대상 HTTP 상태 코드
RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 529}


# ── 폴백 메시지 ──────────────────────────────────────────

FALLBACK_MESSAGES = {
    "morning":  "좋은 아침이에요! 오늘 첫 식사는 가볍게 확인하고 시작해볼까요? 🍃",
    "postmeal": "식사 후 가볍게 움직여볼까요? :)",
}


# ── 재시도 가능 여부 판단 ────────────────────────────────

def _is_retryable(error: Exception) -> bool:
    """재시도할 수 있는 에러인지 판단한다."""
    # Rate limit, 서버 에러
    if isinstance(error, anthropic.APIStatusError):
        return error.status_code in RETRYABLE_STATUS_CODES

    # 네트워크 연결 에러
    if isinstance(error, anthropic.APIConnectionError):
        return True

    # 타임아웃
    if isinstance(error, anthropic.APITimeoutError):
        return True

    return False


# ── API 호출 + 재시도 ────────────────────────────────────

def call_llm_with_retry(client: anthropic.Anthropic, **kwargs):
    """
    client.messages.create()를 재시도 로직으로 감싼다.

    Returns:
        성공 시 API response, 모든 시도 실패 시 None
    """
    kwargs.setdefault("timeout", TIMEOUT_SECONDS)

    for attempt in range(MAX_ATTEMPTS):
        try:
            response = client.messages.create(**kwargs)
            return response

        except Exception as e:
            if not _is_retryable(e):
                print(f"  [fallback] 재시도 불가 에러: {type(e).__name__}: {e}")
                return None

            if attempt < MAX_ATTEMPTS - 1:
                delay = RETRY_DELAYS[min(attempt, len(RETRY_DELAYS) - 1)]
                print(f"  [fallback] 시도 {attempt + 1}/{MAX_ATTEMPTS} 실패: {type(e).__name__}")
                print(f"  [fallback] {delay}초 후 재시도...")
                time.sleep(delay)
            else:
                print(f"  [fallback] 최대 시도({MAX_ATTEMPTS}회) 초과: {type(e).__name__}: {e}")

    return None


def get_fallback_message(agent_type: str) -> str:
    """agent 유형에 맞는 폴백 메시지를 반환한다."""
    return FALLBACK_MESSAGES.get(agent_type, "잠시 후 다시 확인할게요 :)")
