"""
Step 2: Stage 2 XGBoost 훈련

입력: data/processed/shanghai_stage2.csv
출력: models/stage2_meal/{peak.pkl, ttp.pkl, decay.pkl, iauc.pkl}

타겟 4개를 BG 곡선에서 추출 후 독립적으로 XGBoost 회귀 학습.
사용자 기준 train/val/test split (data leakage 방지).
"""
import sys
import json
import pickle
import warnings
import argparse
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.stats import spearmanr
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import mean_absolute_error, mean_squared_error

warnings.filterwarnings("ignore")
sys.stdout.reconfigure(encoding="utf-8")

try:
    import xgboost as xgb
except ImportError:
    raise ImportError("pip install xgboost")

ROOT = Path(__file__).parent.parent
DATA_PATH = ROOT / "data" / "processed" / "shanghai_stage2.csv"
MODEL_DIR = ROOT / "models" / "stage2_meal"
RANDOM_SEED = 42

BG_COLS = [f"BG_{t}min" for t in range(5, 125, 5)]  # 24개
MACRO_FEATURES = ["carbs_g", "protein_g", "fat_g", "fiber_g", "kcal"]
TARGET_NAMES = ["peak_delta", "time_to_peak", "decay_rate", "iauc"]

# 전처리 시 이상치 제거 기준
PRE_GLUCOSE_MIN, PRE_GLUCOSE_MAX = 50.0, 400.0
MIN_CLEAN_BG_POINTS = 20  # 24포인트 중 최소 유효 포인트


# ── 타겟 추출 ──────────────────────────────────────────────────────────────

def extract_targets(row: pd.Series) -> dict | None:
    """
    BG 곡선 → 4개 스칼라 타겟 추출.
    유효하지 않으면 None.
    """
    pre = row["pre_meal_glucose"]
    bg = row[BG_COLS].values.astype(float)
    valid_mask = ~np.isnan(bg)

    if valid_mask.sum() < MIN_CLEAN_BG_POINTS:
        return None

    # 결측 보간 (선형)
    if valid_mask.sum() < len(bg):
        indices = np.arange(len(bg))
        bg = np.interp(indices, indices[valid_mask], bg[valid_mask])

    delta = bg - pre  # 기저치 대비 변화량

    # peak_delta: 최대 상승량 (mg/dL)
    peak_idx = int(np.argmax(delta))
    peak_delta = float(delta[peak_idx])

    # time_to_peak: peak 도달 시간 (분)
    time_to_peak = float((peak_idx + 1) * 5)  # 5분 간격, 1-indexed

    # decay_rate: peak 이후 회복 속도 (peak → 120min 의 기울기, 양수면 빠른 회복)
    post_peak = delta[peak_idx:]
    if len(post_peak) > 1:
        x = np.arange(len(post_peak), dtype=float)
        decay_rate = float(-np.polyfit(x, post_peak, 1)[0])  # 양수 = 하강
    else:
        decay_rate = 0.0

    # iAUC_2h: 기저치 이상의 면적 (trapezoid, 0 이하는 0으로 클리핑)
    iauc = float(np.trapezoid(np.clip(delta, 0, None)) * 5)  # ×5분 = 시간 단위

    return {
        "peak_delta": peak_delta,
        "time_to_peak": time_to_peak,
        "decay_rate": decay_rate,
        "iauc": iauc,
    }


# ── 특징 공학 ──────────────────────────────────────────────────────────────

def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    원본 DataFrame → XGBoost 입력 feature matrix.
    """
    feats = df[MACRO_FEATURES + ["pre_meal_glucose"]].copy()

    # 시간 circadian 인코딩
    meal_time = pd.to_datetime(df["meal_time"], errors="coerce")
    hour = meal_time.dt.hour.fillna(12).astype(float)
    feats["hour_sin"] = np.sin(2 * np.pi * hour / 24)
    feats["hour_cos"] = np.cos(2 * np.pi * hour / 24)

    # 당뇨 타입 수치화
    dtype_map = {"T1DM": 1, "T2DM": 2, "Normal": 0}
    feats["diabetes_type"] = df["diabetes_type"].map(dtype_map).fillna(0).astype(int)

    # 파생 특징: 탄수화물 비율, 단백질+지방 합
    total_macro = feats["carbs_g"] + feats["protein_g"] + feats["fat_g"]
    feats["carb_ratio"] = feats["carbs_g"] / (total_macro + 1e-6)
    feats["protein_fat"] = feats["protein_g"] + feats["fat_g"]

    return feats.reset_index(drop=True)


# ── 사용자 기준 split ─────────────────────────────────────────────────────

def user_split(df: pd.DataFrame, val_ratio=0.15, test_ratio=0.15) -> tuple:
    """
    사용자 단위로 train/val/test 분리 (data leakage 방지).
    """
    np.random.seed(RANDOM_SEED)
    users = df["user_id"].unique()
    np.random.shuffle(users)

    n = len(users)
    n_val = max(1, int(n * val_ratio))
    n_test = max(1, int(n * test_ratio))

    test_users = set(users[:n_test])
    val_users = set(users[n_test:n_test + n_val])
    train_users = set(users[n_test + n_val:])

    train = df[df["user_id"].isin(train_users)].copy()
    val = df[df["user_id"].isin(val_users)].copy()
    test = df[df["user_id"].isin(test_users)].copy()

    print(f"  사용자 split: train={len(train_users)}, val={len(val_users)}, test={len(test_users)}")
    print(f"  행 수: train={len(train)}, val={len(val)}, test={len(test)}")
    return train, val, test


# ── XGBoost 학습 ───────────────────────────────────────────────────────────

XGB_PARAMS = {
    "objective": "reg:squarederror",
    "max_depth": 5,
    "n_estimators": 500,
    "learning_rate": 0.05,
    "subsample": 0.8,
    "colsample_bytree": 0.8,
    "reg_alpha": 0.1,
    "reg_lambda": 1.0,
    "early_stopping_rounds": 50,
    "random_state": RANDOM_SEED,
    "n_jobs": -1,
}


def train_one(name: str, X_train, y_train, X_val, y_val) -> xgb.XGBRegressor:
    model = xgb.XGBRegressor(**XGB_PARAMS, verbosity=0)
    model.fit(
        X_train, y_train,
        eval_set=[(X_val, y_val)],
        verbose=False,
    )
    return model


# ── 평가 ───────────────────────────────────────────────────────────────────

def evaluate(models: dict, X_test: pd.DataFrame, targets_test: pd.DataFrame) -> None:
    print("\n[평가 결과]")
    for name in TARGET_NAMES:
        y_true = targets_test[name].values
        y_pred = models[name].predict(X_test)
        mae = mean_absolute_error(y_true, y_pred)
        rmse = mean_squared_error(y_true, y_pred) ** 0.5
        print(f"  {name:<15} MAE={mae:.2f}  RMSE={rmse:.2f}")

    # Pairwise ranking accuracy (핵심 지표)
    print("\n[Pairwise Ranking Accuracy]")
    for name in ["peak_delta", "iauc"]:
        y_true = targets_test[name].values
        y_pred = models[name].predict(X_test)

        n = len(y_true)
        idx = np.random.default_rng(RANDOM_SEED).choice(n, size=min(5000, n * (n-1) // 2), replace=True)
        i = np.random.default_rng(RANDOM_SEED).integers(0, n, size=5000)
        j = np.random.default_rng(RANDOM_SEED + 1).integers(0, n, size=5000)
        valid = i != j
        i, j = i[valid], j[valid]

        true_rank = (y_true[i] > y_true[j])
        pred_rank = (y_pred[i] > y_pred[j])
        acc = (true_rank == pred_rank).mean()
        corr, _ = spearmanr(y_true, y_pred)
        print(f"  {name:<15} ranking_acc={acc:.3f}  spearman_r={corr:.3f}")

    # Feature importance
    print("\n[Feature Importance - peak_delta]")
    fi = pd.Series(
        models["peak_delta"].feature_importances_,
        index=models["peak_delta"].get_booster().feature_names,
    ).sort_values(ascending=False)
    for feat, imp in fi.head(8).items():
        print(f"  {feat:<25} {imp:.4f}")


# ── 저장 ───────────────────────────────────────────────────────────────────

def save_models(models: dict, feature_names: list) -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for name, model in models.items():
        path = MODEL_DIR / f"{name}.pkl"
        with open(path, "wb") as f:
            pickle.dump(model, f)
        print(f"  저장: {path}")

    meta = {
        "feature_names": feature_names,
        "target_names": TARGET_NAMES,
        "random_seed": RANDOM_SEED,
    }
    with open(MODEL_DIR / "meta.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)


# ── 메인 ───────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=str(DATA_PATH), help="enriched CSV 경로")
    args = parser.parse_args()

    print("=== Stage 2 XGBoost 훈련 ===")
    df = pd.read_csv(args.data)
    print(f"로드: {len(df)}행")

    # 이상치 제거
    df = df[
        (df["pre_meal_glucose"] >= PRE_GLUCOSE_MIN) &
        (df["pre_meal_glucose"] <= PRE_GLUCOSE_MAX)
    ].copy()
    print(f"이상치 제거 후: {len(df)}행")

    # 타겟 추출
    print("\n타겟 추출 중...")
    target_rows = []
    valid_indices = []
    for idx, row in df.iterrows():
        t = extract_targets(row)
        if t is not None:
            target_rows.append(t)
            valid_indices.append(idx)

    df = df.loc[valid_indices].reset_index(drop=True)
    targets_df = pd.DataFrame(target_rows)
    print(f"유효 식사 이벤트: {len(df)}개")
    print("\n타겟 분포:")
    print(targets_df.describe().round(2).to_string())

    # 특징 추출
    X = build_features(df)
    feature_names = X.columns.tolist()
    print(f"\n특징 수: {len(feature_names)}")

    # Split
    print("\n사용자 기준 split:")
    idx_train, idx_val, idx_test = user_split(df)

    def get_split(idx_df):
        idx = idx_df.index
        return X.loc[idx], targets_df.loc[idx]

    X_train, t_train = get_split(idx_train)
    X_val, t_val = get_split(idx_val)
    X_test, t_test = get_split(idx_test)

    # 훈련
    print("\n훈련 중...")
    models = {}
    for name in TARGET_NAMES:
        print(f"  {name}...", end=" ", flush=True)
        model = train_one(name, X_train, t_train[name], X_val, t_val[name])
        models[name] = model
        print(f"best_iteration={model.best_iteration}")

    # 평가
    evaluate(models, X_test, t_test)

    # 저장
    print("\n모델 저장:")
    save_models(models, feature_names)
    print("\n완료.")


if __name__ == "__main__":
    main()
