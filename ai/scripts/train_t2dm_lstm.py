"""
T2DM-only LSTM 학습: T1DM 데이터 제외, T2DM 96명만 사용.

동기:
  - T1DM 환자는 인슐린 주사 타이밍이 혈당 곡선을 지배해서
    음식 → 혈당 신호가 인슐린 노이즈에 묻힘.
  - T2DM/정상은 내인성 인슐린이 일관되게 반응하므로
    음식 macro → 혈당 반응 학습이 가능.
  - 제품은 모든 사용자 대상이지만, 학습 데이터 품질 개선 목적으로
    T1DM 제외. 추론 시에는 모든 타입에 동일 모델 적용.

아키텍처:
  - ShanghaiLSTM (Fix #4: diabetes_type Embedding)
  - Linear prior + LSTM residual (Fix #5)
  - Peak-weighted MSE loss (Fix #3)
  - CosineAnnealingLR, early stopping

출력:
  - models/lstm_meal_t2dm.pt
  - models/lstm_t2dm_scaler.pkl
  - eval/t2dm_compare/comparison_table.png
  - eval/t2dm_compare/lstm_curves.png
  - eval/t2dm_compare/stage2_curves.png
  - eval/t2dm_compare/results.json
"""
from __future__ import annotations

import json
import pickle
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))
sys.stdout.reconfigure(encoding="utf-8")

from sklearn.preprocessing import StandardScaler

from evaluate import generate_comparison_table, plot_curve_examples
from train_stage2_lstm import (
    BG_COLS,
    DTYPE_MAP_PREDICTOR,
    KEY_HORIZONS,
    LABEL_STEPS,
    RANDOM_SEED,
    ShanghaiLSTM,
    build_features,
    build_prior_deltas,
    build_targets,
    delta_to_absolute,
    filter_valid,
    predict_lstm,
    predict_stage2,
    print_metrics,
    train_lstm,
    user_split,
)

# ── 경로 (T2DM 전용) ──────────────────────────────────────────────────────────

DATA_PATH  = ROOT / "data" / "processed" / "shanghai_stage2.csv"
MODEL_PATH  = ROOT / "models" / "lstm_meal_t2dm_coef15.pt"
SCALER_PATH = ROOT / "models" / "lstm_t2dm_scaler_coef15.pkl"
OUT_DIR     = ROOT / "eval" / "t2dm_compare"

T1DM_TYPES = {"T1DM", "T1D"}

# 탄수화물 prior 계수.
# 0.80: 기본값 (메트포르민 T2DM 기준, Zewei 2015)
# 1.35: 훈련 데이터 prior/actual 비율 보정값
# 1.50: 비약물 일반인 대상 보수적 상향값
# 2.00: 비약물 일반인 대상 적극적 상향값
CARB_PRIOR_COEF: float = 1.50


# ── T2DM 필터 ─────────────────────────────────────────────────────────────────

def filter_t2dm(df: pd.DataFrame) -> pd.DataFrame:
    """T1DM 제외: T2DM + Normal 사용자만 유지."""
    before = len(df)
    df = df[~df["diabetes_type"].isin(T1DM_TYPES)].copy()
    after  = len(df)
    n_users = df["user_id"].nunique()
    print(f"  T1DM 제외: {before}행 → {after}행 ({n_users}명)")
    return df.reset_index(drop=True)


# ── 메인 ─────────────────────────────────────────────────────────────────────

def main() -> None:
    print("=" * 60)
    print("T2DM-only LSTM vs XGBoost(Stage2) 비교")
    print("=" * 60)

    # ── 1. 데이터 로드 & 필터 ────────────────────────────────────────────────
    print(f"\n[1] 데이터 로드: {DATA_PATH}")
    df = pd.read_csv(DATA_PATH)
    print(f"  원본: {len(df)}행")
    df = filter_valid(df)
    df = filter_t2dm(df)
    print(f"  최종: {len(df)}행 / {df['user_id'].nunique()}명")

    X_all     = build_features(df)
    delta_all = build_targets(df)

    # ── 2. User 기준 split ───────────────────────────────────────────────────
    print("\n[2] User 기준 split (seed=42, 70/15/15):")
    df_train, df_val, df_test = user_split(df)
    print(f"  train={len(df_train)} ({df_train['user_id'].nunique()}명)"
          f"  val={len(df_val)} ({df_val['user_id'].nunique()}명)"
          f"  test={len(df_test)} ({df_test['user_id'].nunique()}명)")

    idx_train = df_train.index
    idx_val   = df_val.index
    idx_test  = df_test.index

    X_train = X_all[idx_train]
    X_val   = X_all[idx_val]
    X_test  = X_all[idx_test]
    y_test  = delta_all[idx_test]

    # ── 3. Linear prior delta 계산 ──────────────────────────────────────────
    print(f"\n[3] Linear prior delta 계산 (carb_coef={CARB_PRIOR_COEF}):")
    prior_all   = build_prior_deltas(df, carb_coef=CARB_PRIOR_COEF)
    prior_train = prior_all[idx_train]
    prior_val   = prior_all[idx_val]
    prior_test  = prior_all[idx_test]
    print(f"  prior peak 평균: {prior_train.max(axis=1).mean():.2f} mg/dL delta")

    y_train_res = delta_all[idx_train] - prior_train
    y_val_res   = delta_all[idx_val]   - prior_val
    print(f"  residual mean: {y_train_res.mean():.2f}, std: {y_train_res.std():.2f}")

    # ── 4. 정규화 ────────────────────────────────────────────────────────────
    print("\n[4] 정규화 (train fit):")
    feat_scaler = StandardScaler().fit(X_train)
    bg_scaler   = StandardScaler().fit(y_train_res.reshape(-1, 1))

    X_train_s = feat_scaler.transform(X_train).astype(np.float32)
    y_train_s = bg_scaler.transform(y_train_res.reshape(-1, 1)).reshape(-1, 24).astype(np.float32)
    X_val_s   = feat_scaler.transform(X_val).astype(np.float32)
    y_val_s   = bg_scaler.transform(y_val_res.reshape(-1, 1)).reshape(-1, 24).astype(np.float32)
    X_test_s  = feat_scaler.transform(X_test).astype(np.float32)
    print(f"  feature scaler mean[:3]: {feat_scaler.mean_[:3].round(2)}")
    print(f"  residual scaler mean: {bg_scaler.mean_[0]:.2f}, std: {bg_scaler.scale_[0]:.2f}")

    # ── 5. LSTM 학습 ─────────────────────────────────────────────────────────
    print("\n[5] ShanghaiLSTM 학습 (T2DM-only, linear prior residual):")
    model = train_lstm(X_train_s, y_train_s, X_val_s, y_val_s)

    # ── 6. 모델 저장 ─────────────────────────────────────────────────────────
    print("\n[6] 모델 & 스케일러 저장:")
    torch.save(model.state_dict(), MODEL_PATH)
    with open(SCALER_PATH, "wb") as f:
        pickle.dump({"feature": feat_scaler, "bg_target": bg_scaler,
                     "carb_coef": CARB_PRIOR_COEF}, f)
    print(f"  {MODEL_PATH}")
    print(f"  {SCALER_PATH}")

    # ── 7. 예측 ─────────────────────────────────────────────────────────────
    print("\n[7] Test set 예측 (T2DM test users):")
    print("  LSTM 추론 중...")
    y_pred_lstm_delta = predict_lstm(model, X_test_s, bg_scaler, prior_deltas=prior_test)

    print("  Stage2(XGBoost) 추론 중...")
    y_pred_stage2 = predict_stage2(df_test.reset_index(drop=True))

    pre_glucose_test = df_test["pre_meal_glucose"].values.astype(np.float32)
    y_pred_lstm = delta_to_absolute(y_pred_lstm_delta, pre_glucose_test)
    y_test_abs  = delta_to_absolute(y_test, pre_glucose_test)

    # ── 8. 평가 ─────────────────────────────────────────────────────────────
    print("\n[8] 성능 비교 (T2DM test set, raw mg/dL 기준):")
    metrics_lstm   = print_metrics("T2DM-LSTM+Prior",  y_test_abs, y_pred_lstm)
    metrics_stage2 = print_metrics("Stage2(XGBoost)",  y_test_abs, y_pred_stage2)

    metrics_lstm["model_name"]   = "T2DM-LSTM+LinearPrior"
    metrics_lstm["model_type"]   = "lstm"
    metrics_stage2["model_name"] = "Stage2(XGBoost+LinearPrior)"
    metrics_stage2["model_type"] = "xgboost"

    # ── 9. 결과 저장 ────────────────────────────────────────────────────────
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    results_path = OUT_DIR / "results.json"
    with open(results_path, "w", encoding="utf-8") as f:
        json.dump({"lstm": metrics_lstm, "stage2": metrics_stage2},
                  f, indent=2, ensure_ascii=False)
    print(f"\n[9] 결과 저장: {results_path}")

    generate_comparison_table(
        [metrics_lstm, metrics_stage2],
        save_path=OUT_DIR / "comparison_table.png",
    )
    plot_curve_examples(
        y_test_abs, y_pred_lstm,
        save_path=OUT_DIR / "lstm_curves.png",
        n_examples=5,
        title_prefix="T2DM-LSTM+Prior: ",
    )
    plot_curve_examples(
        y_test_abs, y_pred_stage2,
        save_path=OUT_DIR / "stage2_curves.png",
        n_examples=5,
        title_prefix="Stage2(XGBoost): ",
    )
    print(f"  {OUT_DIR}/comparison_table.png")
    print(f"  {OUT_DIR}/lstm_curves.png")
    print(f"  {OUT_DIR}/stage2_curves.png")
    print("\n완료.")


if __name__ == "__main__":
    main()
