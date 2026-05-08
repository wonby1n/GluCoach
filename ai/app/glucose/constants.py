"""혈당 예측 모듈 공통 상수.

DATA_SPEC.md 와 동기. 변경 시 두 곳 모두 업데이트.
"""

from __future__ import annotations

# ── 인코딩 매핑 (preprocess.py 와 동기) ──────────────────────────────

DIABETES_TYPE_MAP: dict[str, int] = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP: dict[str, int] = {"low": 0, "medium": 1, "high": 2}
MEAL_PATTERN_MAP: dict[str, int] = {
    "regular_3": 0,
    "skip_breakfast": 1,
    "skip_dinner": 2,
    "skip_lunch": 3,
    "skip_breakfast_dinner": 4,
    "skip_breakfast_lunch": 5,
    "skip_lunch_dinner": 6,
    "frequent_small": 7,
    "irregular": 8,
    "late_dinner": 9,
    "fasting_day": 10,
}

# ── 컬럼 정의 ─────────────────────────────────────────────────────────

LABEL_STEPS: list[int] = list(range(5, 125, 5))  # [5, 10, ..., 120]
LABEL_COLS: list[str] = [f"BG_{t}min" for t in LABEL_STEPS]  # 24개

# Model 1 입력 (식사 시점)
MEAL_CONTINUOUS_COLS: list[str] = [
    "carbs",
    "meal_time_sin",
    "meal_time_cos",
    "current_glucose",
    "fasting_bg",
    "weight_kg",
]
MEAL_CATEGORICAL_COLS: list[str] = ["activity", "diabetes_type", "meal_pattern"]
MEAL_FEATURE_COLS: list[str] = MEAL_CONTINUOUS_COLS + MEAL_CATEGORICAL_COLS

# scaler.pkl["model1_features"] 가 정규화하는 컬럼 (sin/cos 제외)
MEAL_SCALE_COLS: list[str] = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]

# 카테고리 cardinality (임베딩용)
CATEGORICAL_CARDINALITIES: tuple[int, int, int] = (3, 3, 11)  # activity, diabetes_type, meal_pattern

# ── 모델 사양 ────────────────────────────────────────────────────────

OUTPUT_DIM: int = 24                # 식후/향후 BG 시점 개수
SEQ_LEN: int = 12                   # Model 2 입력 길이 (60분)
PROFILE_DIM: int = 4                # Model 2 profile dim (weight_kg, fasting_bg, activity, diabetes_type)
EMBEDDING_DIM: int = 8              # 카테고리 임베딩 차원
HIDDEN_DIM: int = 64

# ── 경로 (ai/ 루트 기준) ─────────────────────────────────────────────

DEFAULT_PROCESSED_DIR: str = "data/processed"
DEFAULT_TIMESERIES_DIR: str = "data/processed/timeseries"
DEFAULT_MODELS_DIR: str = "models"
DEFAULT_SCALER_PATH: str = "models/scaler.pkl"
DEFAULT_USER_SPLIT_PATH: str = "models/user_split.json"
