"""런타임 환경변수 설정.

학습 스크립트(train_base.py, train_timeseries.py 등)는 constants.py 를 그대로 쓴다.
이 파일은 서버 런타임(predict, personalize, API)에서만 사용.

환경변수 없으면 constants.py 기본값으로 동작 (기존 동작과 동일).
"""

from __future__ import annotations

import os

# ── 경로 ─────────────────────────────────────────────────────────────
MODELS_DIR: str = os.getenv("GLUCOSE_MODELS_DIR", "models")
SCALER_PATH: str = os.getenv(
    "GLUCOSE_SCALER_PATH", os.path.join(MODELS_DIR, "scaler.pkl")
)

# ── 예측 모델 선택 ────────────────────────────────────────────────────
# lstm | mlp | ridge
MEAL_MODEL_TYPE: str = os.getenv("GLUCOSE_MEAL_MODEL_TYPE", "lstm")

# ── 파인튜닝 파라미터 ─────────────────────────────────────────────────
FINETUNE_EPOCHS: int = int(os.getenv("GLUCOSE_FINETUNE_EPOCHS", "30"))
FINETUNE_LR: float = float(os.getenv("GLUCOSE_FINETUNE_LR", "0.0003"))
FINETUNE_RECENT_N: int = int(os.getenv("GLUCOSE_FINETUNE_RECENT_N", "42"))
PERSONALIZED_MAX_AGE_DAYS: int = int(
    os.getenv("GLUCOSE_PERSONALIZED_MAX_AGE_DAYS", "90")
)
