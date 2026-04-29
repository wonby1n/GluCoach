"""LLM 리포트 (식사 코멘트 + 주간 리포트).

anthropic SDK 사용. ANTHROPIC_API_KEY 가 필수.

함수:
- generate_meal_comment: 식사 1건에 대한 따뜻한 코멘트 (2-3문장 한국어)
- generate_weekly_report: 1주일 통계로 요약/하이라이트/제안 JSON

실패 시 fallback 메시지 반환 (시연 안정성).
"""

from __future__ import annotations

import json
import logging
import os
from typing import Any

logger = logging.getLogger(__name__)


# 모델 ID — 2026-04 기준 최신 클래스
DEFAULT_MODEL = "claude-sonnet-4-6"
DEFAULT_MAX_TOKENS = 400
DEFAULT_TIMEOUT_SEC = 10.0


def _get_client() -> Any:
    """anthropic 클라이언트 lazy 생성. 키 없으면 RuntimeError."""
    try:
        import anthropic  # type: ignore
    except ImportError as e:
        raise RuntimeError("anthropic SDK 미설치: pip install anthropic") from e
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        raise RuntimeError("ANTHROPIC_API_KEY 미설정")
    return anthropic.Anthropic(api_key=api_key)


# ─────────────────────────────────────────────────────────────────────
# 식사 코멘트
# ─────────────────────────────────────────────────────────────────────


_MEAL_SYSTEM = (
    "당신은 당뇨 환자를 위한 따뜻한 영양 코치입니다. "
    "식사 정보와 예측된 혈당 곡선을 보고 2-3문장으로 친근하게 한국어로 코멘트하세요. "
    "전문 용어는 피하고, 응원하는 톤으로. 의학적 진단/처방은 하지 마세요."
)

_MEAL_FALLBACK = "이 식사가 혈당 변화에 영향을 줄 수 있어요. 식후 가벼운 산책 어떠세요?"


def generate_meal_comment(
    meal_info: dict[str, Any],
    predicted_curve: dict[str, Any],
    model: str = DEFAULT_MODEL,
) -> str:
    """식사 + 곡선 → 코멘트 1개.

    meal_info: {"name": str, "carbs": float, "time": str}
    predicted_curve: {"horizons_min": [...], "predicted": [...]}
    """
    user_msg = (
        f"식사: {meal_info.get('name', '식사')} (탄수화물 {meal_info.get('carbs', 0)}g, "
        f"{meal_info.get('time', '')})\n"
        f"예측된 식후 혈당 곡선 (mg/dL, 5~120분 5분 간격): "
        f"{predicted_curve.get('predicted', [])}\n\n"
        f"위 정보를 보고 환자가 듣기 좋은 코멘트 2-3문장으로 부탁드립니다."
    )

    try:
        client = _get_client()
        resp = client.messages.create(
            model=model,
            max_tokens=DEFAULT_MAX_TOKENS,
            timeout=DEFAULT_TIMEOUT_SEC,
            system=_MEAL_SYSTEM,
            messages=[{"role": "user", "content": user_msg}],
        )
        text = "".join(block.text for block in resp.content if hasattr(block, "text"))
        return text.strip() or _MEAL_FALLBACK
    except Exception:
        logger.exception("meal comment LLM 호출 실패")
        return _MEAL_FALLBACK


# ─────────────────────────────────────────────────────────────────────
# 주간 리포트
# ─────────────────────────────────────────────────────────────────────


_WEEKLY_SYSTEM = (
    "당신은 당뇨 환자의 주간 혈당 데이터를 요약해서 응원하는 영양 코치입니다. "
    "출력은 반드시 다음 JSON 형식만 (다른 텍스트 X):\n"
    '{"summary": "한 문단 요약", '
    '"highlights": ["좋은점1", "좋은점2", "좋은점3"], '
    '"suggestions": ["제안1", "제안2"]}'
)

_WEEKLY_FALLBACK: dict[str, Any] = {
    "summary": "이번 주 혈당 데이터를 검토했어요.",
    "highlights": [
        "꾸준히 기록을 남기고 계세요.",
        "식사 후 곡선을 잘 관찰하셨어요.",
        "큰 저혈당 없이 한 주를 보내셨어요.",
    ],
    "suggestions": [
        "식후 30분 가벼운 산책을 시도해 보세요.",
        "섬유질이 풍부한 식사를 추가해 보세요.",
    ],
}


def generate_weekly_report(
    weekly_data: dict[str, Any],
    model: str = DEFAULT_MODEL,
) -> dict[str, Any]:
    """주간 통계 → JSON 리포트.

    weekly_data 예시:
        {"avg_glucose": 145, "tir": 0.65, "peak_meals": [...], "hypoglycemia_count": 1}
    """
    user_msg = (
        "다음 주간 혈당 데이터로 환자에게 보낼 리포트를 작성해주세요.\n\n"
        f"{json.dumps(weekly_data, ensure_ascii=False, indent=2)}\n\n"
        "응답은 반드시 정해진 JSON 만."
    )

    try:
        client = _get_client()
        resp = client.messages.create(
            model=model,
            max_tokens=DEFAULT_MAX_TOKENS * 2,
            timeout=DEFAULT_TIMEOUT_SEC * 2,
            system=_WEEKLY_SYSTEM,
            messages=[{"role": "user", "content": user_msg}],
        )
        text = "".join(block.text for block in resp.content if hasattr(block, "text"))
        text = text.strip()
        # JSON 블록만 추출
        start = text.find("{")
        end = text.rfind("}")
        if start == -1 or end == -1:
            raise ValueError("JSON 응답 형식 아님")
        parsed = json.loads(text[start : end + 1])
        return _validate_weekly_schema(parsed)
    except Exception:
        logger.exception("weekly report LLM 호출 실패")
        return dict(_WEEKLY_FALLBACK)


def _validate_weekly_schema(data: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(data, dict):
        raise ValueError("not a dict")
    for key in ("summary", "highlights", "suggestions"):
        if key not in data:
            raise ValueError(f"missing {key}")
    if not isinstance(data["highlights"], list):
        raise ValueError("highlights not a list")
    if not isinstance(data["suggestions"], list):
        raise ValueError("suggestions not a list")
    return data


# ─────────────────────────────────────────────────────────────────────
# 더미 테스트
# ─────────────────────────────────────────────────────────────────────


if __name__ == "__main__":
    print("[meal comment]")
    comment = generate_meal_comment(
        {"name": "라면", "carbs": 80, "time": "12:30"},
        {"horizons_min": [5, 30, 60, 90, 120], "predicted": [110, 165, 145, 130, 120]},
    )
    print(comment)

    print("\n[weekly report]")
    report = generate_weekly_report(
        {
            "avg_glucose": 145,
            "tir": 0.65,
            "peak_meals": ["라면", "비빔밥"],
            "hypoglycemia_count": 1,
        }
    )
    print(json.dumps(report, ensure_ascii=False, indent=2))
