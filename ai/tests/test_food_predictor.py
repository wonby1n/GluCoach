"""FoodPredictor._merge_and_rank 단위 테스트.

표시명 기반 머지 + max confidence + Top-k 정렬을 모델 로드 없이 검증.
"""
import torch

from app.models.food_predictor import FoodPredictor


def _make_predictor(class_names, code_to_name):
    """모델/DB 로드 없이 _merge_and_rank 만 호출하기 위한 헬퍼."""
    p = FoodPredictor.__new__(FoodPredictor)
    p.class_names = class_names
    p.code_to_name = code_to_name
    return p


def test_no_duplicates_returns_top_k_in_desc_order():
    p = _make_predictor(
        class_names=["01011001", "02011027", "02011029"],
        code_to_name={"01011001": "쌀밥", "02011027": "짬뽕", "02011029": "쫄면"},
    )
    sims = torch.tensor([0.3, 0.9, 0.6])
    out = p._merge_and_rank(sims, top_k=3)
    assert [r["name_ko"] for r in out] == ["짬뽕", "쫄면", "쌀밥"]
    assert out[0]["confidence"] == 0.9


def test_duplicate_display_name_merges_with_max():
    # 코드 키와 커스텀 키가 같은 표시명으로 매핑되는 경우.
    p = _make_predictor(
        class_names=["01015017", "국밥_돼지머리", "02011027"],
        code_to_name={
            "01015017": "돼지국밥",
            "국밥_돼지머리": "돼지국밥",
            "02011027": "짬뽕",
        },
    )
    sims = torch.tensor([0.7, 0.65, 0.6])
    out = p._merge_and_rank(sims, top_k=5)
    assert len(out) == 2
    assert out[0] == {"name_ko": "돼지국밥", "confidence": 0.7}
    assert out[1] == {"name_ko": "짬뽕", "confidence": 0.6}


def test_unmapped_code_falls_back_to_key():
    p = _make_predictor(
        class_names=["01015017", "99999999"],
        code_to_name={"01015017": "돼지국밥"},
    )
    sims = torch.tensor([0.4, 0.8])
    out = p._merge_and_rank(sims, top_k=2)
    assert out[0]["name_ko"] == "99999999"
    assert out[1]["name_ko"] == "돼지국밥"


def test_top_k_caps_at_unique_display_count():
    # 표시명이 모두 동일하면 top_k=5 요청해도 1개만 반환.
    p = _make_predictor(
        class_names=["a", "b", "c"],
        code_to_name={"a": "X", "b": "X", "c": "X"},
    )
    sims = torch.tensor([0.1, 0.5, 0.3])
    out = p._merge_and_rank(sims, top_k=5)
    assert len(out) == 1
    assert out[0] == {"name_ko": "X", "confidence": 0.5}
