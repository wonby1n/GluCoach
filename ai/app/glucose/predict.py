"""실제 추론 로직 (학습된 모델 + scaler 사용).

interface.py 의 dummy 를 대체. 모델/scaler 가 없으면 RuntimeError 발생 →
interface.py 가 try/except 로 dummy 폴백.

두 Predictor:
- MealPredictor: Model 1 (식사 시점 → 식후 120분)
- NowPredictor:  Model 2 (현재 시점 → 향후 120분)
"""

from __future__ import annotations

import math
import threading
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import torch

from app.glucose import config as _cfg
from app.glucose.constants import (
    ACTIVITY_MAP,
    DEFAULT_MODELS_DIR,
    DEFAULT_SCALER_PATH,
    DIABETES_TYPE_MAP,
    LABEL_STEPS,
    MEAL_PATTERN_MAP,
    MEAL_SCALE_COLS,
    SEQ_LEN,
)
from app.glucose.data_loader import load_scaler
from app.glucose.model import (
    MealLSTMDecoder,
    MealMLP,
    MealRidge,
    NowLSTM,
    load_torch_model,
)


# ─────────────────────────────────────────────────────────────────────
# 공통 헬퍼
# ─────────────────────────────────────────────────────────────────────


def _parse_iso_to_hour(time_iso: str) -> float:
    """ISO 8601 → hour float (8:30 → 8.5). naive datetime 가정 (KST)."""
    # fromisoformat 은 timezone offset 도 처리
    dt = datetime.fromisoformat(time_iso.replace("Z", "+00:00"))
    return dt.hour + dt.minute / 60.0 + dt.second / 3600.0


def _meal_time_cyclic(hour: float) -> tuple[float, float]:
    angle = 2 * math.pi * hour / 24.0
    return math.sin(angle), math.cos(angle)


def _scale_meal_features(
    carbs: float,
    current_glucose: float,
    fasting_bg: float,
    weight_kg: float,
    feature_scaler: Any,
) -> np.ndarray:
    """4개 컬럼 [carbs, current_glucose, fasting_bg, weight_kg] 정규화."""
    raw = np.array([[carbs, current_glucose, fasting_bg, weight_kg]], dtype=np.float32)
    return feature_scaler.transform(raw)[0]  # [4]


def _scale_profile(
    weight_kg: float,
    fasting_bg: float,
    profile_scaler: Any,
) -> np.ndarray:
    """Model 2 profile 정규화 — weight_kg, fasting_bg 만."""
    raw = np.array([[weight_kg, fasting_bg]], dtype=np.float32)
    return profile_scaler.transform(raw)[0]  # [2]


def _inverse_bg(y_normalized: np.ndarray, bg_scaler: Any) -> np.ndarray:
    """[24] 정규화 BG → raw mg/dL."""
    flat = y_normalized.reshape(-1, 1)
    return bg_scaler.inverse_transform(flat).flatten()


# ─────────────────────────────────────────────────────────────────────
# Model 1: 식사 시점
# ─────────────────────────────────────────────────────────────────────


class MealPredictor:
    """Model 1 추론. ridge / mlp / lstm 중 선택.

    user_id 가 주어지면 lstm_meal_personalized_{user_id}.pt 를 우선 로드.
    개인화 파일 없으면 베이스 모델로 폴백.
    """

    def __init__(
        self,
        model_type: str = "lstm",
        models_dir: str | Path = DEFAULT_MODELS_DIR,
        user_id: str | None = None,
    ) -> None:
        self.model_type = model_type
        self.models_dir = Path(models_dir)
        self.user_id = user_id
        self.mode = "base"
        self.scaler: dict[str, Any] | None = None
        self.model: Any | None = None
        self._device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    def _ensure_loaded(self) -> None:
        if self.scaler is None:
            self.scaler = load_scaler()
        if self.model is None:
            if self.model_type == "ridge":
                path = self.models_dir / "ridge_meal.pkl"
                if not path.exists():
                    raise RuntimeError(f"{path} 없음 — Model 1 학습 필요")
                self.model = MealRidge.load(path)
            elif self.model_type == "mlp":
                path = self.models_dir / "mlp_meal.pt"
                if not path.exists():
                    raise RuntimeError(f"{path} 없음 — Model 1 학습 필요")
                m, _ = load_torch_model(path, MealMLP)
                self.model = m.to(self._device)
            elif self.model_type == "lstm":
                if self.user_id:
                    p_path = self.models_dir / f"lstm_meal_personalized_{self.user_id}.pt"
                    if p_path.exists():
                        m, _ = load_torch_model(p_path, MealLSTMDecoder)
                        self.model = m.to(self._device)
                        self.mode = "personalized"
                        return
                path = self.models_dir / "lstm_meal.pt"
                if not path.exists():
                    raise RuntimeError(f"{path} 없음 — Model 1 학습 필요")
                m, _ = load_torch_model(path, MealLSTMDecoder)
                self.model = m.to(self._device)
            else:
                raise ValueError(f"unknown model_type: {self.model_type}")

    def encode(self, request: dict[str, Any]) -> tuple[np.ndarray, np.ndarray]:
        """요청 dict → (X_continuous[6], X_categorical[3])."""
        self._ensure_loaded()
        recent: list[float] = request.get("recent_values") or []
        if not recent:
            raise ValueError("recent_values is required")
        current_glucose = float(recent[-1])

        meal = request["meal"]
        carbs = float(meal["carbs"])
        hour = _parse_iso_to_hour(meal["time_iso"])
        sin_t, cos_t = _meal_time_cyclic(hour)

        profile = request["user_profile"]
        fasting_bg = float(profile["fasting_bg"])
        weight_kg = float(profile["weight_kg"])

        try:
            activity = ACTIVITY_MAP[profile["activity"]]
            diabetes_type = DIABETES_TYPE_MAP[profile["diabetes_type"]]
            meal_pattern = MEAL_PATTERN_MAP[profile["meal_pattern"]]
        except KeyError as e:
            raise ValueError(f"unknown enum value: {e}") from e

        # 정규화
        scaled4 = _scale_meal_features(
            carbs, current_glucose, fasting_bg, weight_kg, self.scaler["model1_features"]
        )
        # 컬럼 순서: [carbs, meal_time_sin, meal_time_cos, current_glucose, fasting_bg, weight_kg]
        x_cont = np.array(
            [scaled4[0], sin_t, cos_t, scaled4[1], scaled4[2], scaled4[3]],
            dtype=np.float32,
        )
        x_cat = np.array([activity, diabetes_type, meal_pattern], dtype=np.int64)
        return x_cont, x_cat

    def predict(self, request: dict[str, Any]) -> list[float]:
        """요청 dict → 24개 BG (raw mg/dL)."""
        x_cont, x_cat = self.encode(request)

        if self.model_type == "ridge":
            y_norm = self.model.predict(x_cont[None, :], x_cat[None, :])[0]
        else:
            self.model.eval()
            with torch.no_grad():
                xc = torch.from_numpy(x_cont).unsqueeze(0).to(self._device)
                xa = torch.from_numpy(x_cat).unsqueeze(0).to(self._device)
                y_norm = self.model(xc, xa).cpu().numpy()[0]

        y_raw = _inverse_bg(y_norm, self.scaler["bg_target"])
        return [round(float(v), 2) for v in y_raw]


# ─────────────────────────────────────────────────────────────────────
# Model 2: 현재 시점
# ─────────────────────────────────────────────────────────────────────


class NowPredictor:
    def __init__(self, models_dir: str | Path = DEFAULT_MODELS_DIR) -> None:
        self.models_dir = Path(models_dir)
        self.scaler: dict[str, Any] | None = None
        self.model: NowLSTM | None = None
        self._device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

    def _ensure_loaded(self) -> None:
        if self.scaler is None:
            self.scaler = load_scaler()
        if self.model is None:
            path = self.models_dir / "lstm_now.pt"
            if not path.exists():
                raise RuntimeError(f"{path} 없음 — Model 2 학습 필요")
            m, _ = load_torch_model(path, NowLSTM)
            self.model = m.to(self._device)

    def encode(self, request: dict[str, Any]) -> tuple[np.ndarray, np.ndarray]:
        """요청 dict → (X_seq[12], X_profile[4])."""
        self._ensure_loaded()
        recent: list[float] = request.get("recent_values") or []
        if len(recent) < SEQ_LEN:
            raise ValueError(f"recent_values must have >= {SEQ_LEN}, got {len(recent)}")
        last_12 = np.array(recent[-SEQ_LEN:], dtype=np.float32).reshape(-1, 1)
        x_seq = self.scaler["bg_target"].transform(last_12).flatten()  # [12]

        profile = request["user_profile"]
        fasting_bg = float(profile["fasting_bg"])
        weight_kg = float(profile["weight_kg"])
        try:
            activity = ACTIVITY_MAP[profile["activity"]]
            diabetes_type = DIABETES_TYPE_MAP[profile["diabetes_type"]]
        except KeyError as e:
            raise ValueError(f"unknown enum value: {e}") from e

        scaled2 = _scale_profile(weight_kg, fasting_bg, self.scaler["profile"])
        x_profile = np.array(
            [scaled2[0], scaled2[1], float(activity), float(diabetes_type)],
            dtype=np.float32,
        )
        return x_seq.astype(np.float32), x_profile

    def predict(self, request: dict[str, Any]) -> list[float]:
        x_seq, x_profile = self.encode(request)
        self.model.eval()
        with torch.no_grad():
            xs = torch.from_numpy(x_seq).unsqueeze(0).to(self._device)
            xp = torch.from_numpy(x_profile).unsqueeze(0).to(self._device)
            y_norm = self.model(xs, xp).cpu().numpy()[0]
        y_raw = _inverse_bg(y_norm, self.scaler["bg_target"])
        return [round(float(v), 2) for v in y_raw]


# ─────────────────────────────────────────────────────────────────────
# Lazy 싱글톤
# ─────────────────────────────────────────────────────────────────────

_meal_predictor: MealPredictor | None = None
_now_predictor: NowPredictor | None = None
_personalized_predictors: dict[str, MealPredictor] = {}
_lock = threading.Lock()


def get_meal_predictor(model_type: str = _cfg.MEAL_MODEL_TYPE, user_id: str | None = None) -> MealPredictor:
    """user_id 가 있으면 개인화 모델 캐시에서, 없으면 베이스 싱글톤에서 반환."""
    global _meal_predictor
    if user_id:
        with _lock:
            if user_id not in _personalized_predictors:
                p = MealPredictor(model_type=model_type, models_dir=_cfg.MODELS_DIR, user_id=user_id)
                p._ensure_loaded()
                _personalized_predictors[user_id] = p
            return _personalized_predictors[user_id]
    with _lock:
        if _meal_predictor is None or _meal_predictor.model_type != model_type:
            _meal_predictor = MealPredictor(model_type=model_type, models_dir=_cfg.MODELS_DIR)
            _meal_predictor._ensure_loaded()
        return _meal_predictor


def get_now_predictor() -> NowPredictor:
    global _now_predictor
    with _lock:
        if _now_predictor is None:
            _now_predictor = NowPredictor(models_dir=_cfg.MODELS_DIR)
            _now_predictor._ensure_loaded()
    return _now_predictor


def reset_predictors(user_id: str | None = None) -> None:
    """모델 파일 갱신 후 캐시 초기화.

    user_id 지정 시 해당 사용자 캐시만 제거.
    None 이면 전체 초기화 (테스트/서버 재시작 용).
    """
    global _meal_predictor, _now_predictor, _personalized_predictors
    with _lock:
        if user_id is not None:
            _personalized_predictors.pop(user_id, None)
        else:
            _meal_predictor = None
            _now_predictor = None
            _personalized_predictors = {}
