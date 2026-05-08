"""
Stage 2 XGBoost 추론 + 선형 prior 블렌딩.

XGBoost는 시간대·식전혈당·당뇨타입 등 개인 컨텍스트를 포착하고,
선형 prior(Zeevi et al., Cell 2015 계수 기반)는 macro → 혈당 방향성을 보장한다.

최종 = XGBoost × α + LinearPrior × (1-α)
"""

from __future__ import annotations

import json
import pickle
import logging
from pathlib import Path
from typing import Any

import numpy as np

logger = logging.getLogger(__name__)

_STAGE2_DIR = Path(__file__).parent.parent.parent / "models" / "stage2_meal"

# 블렌딩 가중치 (XGBoost : LinearPrior)
_ALPHA_PEAK = 0.35   # peak_delta: linear prior 비중 높게 (방향성 중요)
_ALPHA_TTP  = 0.40   # time_to_peak: 중간 블렌딩
_ALPHA_DECAY = 0.60  # decay_rate: XGBoost 신뢰 (방향성 무관)

# 당뇨 타입 숫자 매핑 (학습 시와 동일)
_DTYPE_MAP = {"Normal": 0, "T1D": 1, "T2D": 2}


# ── 혈당 반응 스케일 팩터 (Hill function, n=2) ────────────────────────────

def _glycemic_scale_factor(carbs: float, protein: float) -> float:
    """
    탄수화물 함량에 비례하는 혈당 반응 스케일 [0, 1].

    근거:
    - 임상 기준(ADA, 폐쇄루프 연구): carbs < 10g = 최소/경계 반응, 20g+ = 완전 반응
    - 단백질 gluconeogenesis 기여: ~8% (Fromentin et al., PNAS 2013)
    - Hill function (n=2, T=5): 5g에서 50%, 10g에서 80%, 20g에서 94%

    Args:
        carbs:   탄수화물 g
        protein: 단백질 g (gluconeogenesis 8% 기여)
    """
    effective = max(0.0, carbs + protein * 0.08)
    return (effective ** 2) / (effective ** 2 + 25.0)


# ── 선형 prior 계수 (Zeevi et al. 2015, Shen et al. 2025 기반) ────────────

def _linear_peak_prior(carbs: float, protein: float, fat: float, fiber: float) -> float:
    """
    100g carbs 기준 정규화된 선형 peak_delta 추정 (mg/dL).
    carbs    : +0.80 per g  (주요 혈당 상승 인자)
    fiber    : -1.50 per g  (위 배출 지연, 소화 속도 감소)
    fat      : -0.30 per g  (위 배출 지연으로 peak 완화)
    protein  : -0.20 per g  (인슐린 분비 자극으로 약간 완화)
    base     : +10.0        (소화 자체에 의한 최소 상승)
    """
    val = 10.0 + carbs * 0.80 - fiber * 1.50 - fat * 0.30 - protein * 0.20
    return max(val, 0.0)


def _linear_ttp_prior(carbs: float, fat: float, fiber: float, protein: float) -> float:
    """
    선형 time_to_peak 추정 (분).
    carbs    : -0.10 per g  (순수 탄수화물 → 빠른 peak)
    fat      : +0.50 per g  (위 배출 지연 → 늦은 peak)
    fiber    : +0.30 per g  (점성 섬유 → 늦은 흡수)
    protein  : +0.20 per g  (단백질 공복시 glucagon 자극)
    base     : 35.0 분
    """
    val = 35.0 + fat * 0.50 + fiber * 0.30 + protein * 0.20 - carbs * 0.10
    return float(np.clip(val, 10.0, 110.0))


# ── 모델 로더 ────────────────────────────────────────────────────────────────

class Stage2Predictor:
    """
    4개 XGBoost 모델 + 선형 prior 블렌딩으로 혈당 반응 스칼라 예측.
    """

    def __init__(self, model_dir: Path = _STAGE2_DIR) -> None:
        self._dir = model_dir
        self._models: dict = {}
        self._feature_names: list[str] = []
        self._loaded = False

    def _load(self) -> None:
        if self._loaded:
            return
        meta_path = self._dir / "meta.json"
        if not meta_path.exists():
            raise FileNotFoundError(f"Stage2 meta.json not found: {meta_path}")
        with open(meta_path, encoding="utf-8") as f:
            meta = json.load(f)
        self._feature_names = meta["feature_names"]
        for name in ("peak_delta", "time_to_peak", "decay_rate", "iauc"):
            pkl = self._dir / f"{name}.pkl"
            if not pkl.exists():
                raise FileNotFoundError(f"Stage2 model not found: {pkl}")
            with open(pkl, "rb") as f:
                self._models[name] = pickle.load(f)
        self._loaded = True
        logger.info("Stage2 models loaded from %s", self._dir)

    def _build_feature_row(self, inp: dict[str, Any]) -> "np.ndarray":
        """입력 dict → feature 벡터 (학습 시 feature 순서 유지)."""
        import pandas as pd

        carbs   = float(inp.get("carbs_g",   inp.get("carbs",   0.0)))
        protein = float(inp.get("protein_g", 0.0))
        fat     = float(inp.get("fat_g",     0.0))
        fiber   = float(inp.get("fiber_g",   0.0))
        kcal    = float(inp.get("kcal",      carbs * 4 + protein * 4 + fat * 9))
        pre_gl  = float(inp.get("pre_meal_glucose", inp.get("current_glucose", 100.0)))
        hour    = float(inp.get("meal_hour", 12.0))
        dtype   = _DTYPE_MAP.get(str(inp.get("diabetes_type", "T2D")), 2)

        total = carbs + protein + fat + 1e-6
        row = {
            "carbs_g":         carbs,
            "protein_g":       protein,
            "fat_g":           fat,
            "fiber_g":         fiber,
            "kcal":            kcal,
            "pre_meal_glucose": pre_gl,
            "hour_sin":        np.sin(2 * np.pi * hour / 24),
            "hour_cos":        np.cos(2 * np.pi * hour / 24),
            "diabetes_type":   dtype,
            "carb_ratio":      carbs / total,
            "protein_fat":     protein + fat,
        }
        df = pd.DataFrame([row])
        return df[self._feature_names]

    def predict(self, inp: dict[str, Any]) -> dict[str, float]:
        """
        입력 dict → {peak_delta, time_to_peak, decay_rate, iauc} (보정 후).

        inp 키:
            carbs_g, protein_g, fat_g, fiber_g, kcal (optional),
            pre_meal_glucose (또는 current_glucose),
            meal_hour (0~23, default=12),
            diabetes_type ("Normal"|"T1D"|"T2D", default="T2D")
        """
        self._load()

        carbs   = float(inp.get("carbs_g",   inp.get("carbs",   0.0)))
        protein = float(inp.get("protein_g", 0.0))
        fat     = float(inp.get("fat_g",     0.0))
        fiber   = float(inp.get("fiber_g",   0.0))

        X = self._build_feature_row(inp)

        xgb_peak  = float(self._models["peak_delta"].predict(X)[0])
        xgb_ttp   = float(self._models["time_to_peak"].predict(X)[0])
        xgb_decay = float(self._models["decay_rate"].predict(X)[0])
        xgb_iauc  = float(self._models["iauc"].predict(X)[0])

        lin_peak = _linear_peak_prior(carbs, protein, fat, fiber)
        lin_ttp  = _linear_ttp_prior(carbs, fat, fiber, protein)

        peak_delta   = max(0.0, _ALPHA_PEAK * xgb_peak + (1 - _ALPHA_PEAK) * lin_peak)
        time_to_peak = _ALPHA_TTP * xgb_ttp + (1 - _ALPHA_TTP) * lin_ttp
        time_to_peak = float(np.clip(time_to_peak, 5.0, 120.0))
        decay_rate   = max(0.1, _ALPHA_DECAY * xgb_decay + (1 - _ALPHA_DECAY) * 3.0)

        # 섭취량이 적을수록 혈당 반응 비례 감쇄 (물·보리차 등 near-zero macro 대응)
        scale = _glycemic_scale_factor(carbs, protein)
        peak_delta = peak_delta * scale

        return {
            "peak_delta":   round(peak_delta, 2),
            "time_to_peak": round(time_to_peak, 1),
            "decay_rate":   round(decay_rate, 3),
            "iauc":         round(max(0.0, xgb_iauc) * scale, 1),
        }


# 싱글턴
_predictor: Stage2Predictor | None = None


def get_stage2_predictor() -> Stage2Predictor:
    global _predictor
    if _predictor is None:
        _predictor = Stage2Predictor()
    return _predictor
