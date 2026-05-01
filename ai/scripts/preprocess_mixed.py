"""E2-Mixed: sim_v3 + Shanghai 합성 데이터셋 생성.

전제:
    data/processed/train.csv          (sim, 정규화 완료)
    data/processed/val.csv
    data/processed/test.csv
    models/scaler.pkl                  (sim scaler)

    data/processed/shanghai/train.csv  (Shanghai, 정규화 완료)
    data/processed/shanghai/val.csv
    data/processed/shanghai/test.csv
    models/shanghai/scaler.pkl         (Shanghai scaler)

출력:
    data/processed/mixed/train.csv     (sim+Shanghai, 새 scaler 정규화)
    data/processed/mixed/val.csv
    data/processed/mixed/test.csv      (Shanghai test 만 포함 - 실세계 평가용)
    models/mixed/scaler.pkl
    models/mixed/user_split.json       (참고용)

사용법:
    cd ai/
    python scripts/preprocess_mixed.py
"""

from __future__ import annotations

import os
import pickle
import sys
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parent.parent

SIM_PROCESSED = ROOT / "data/processed"
SHANGHAI_PROCESSED = ROOT / "data/processed/shanghai"
SIM_SCALER = ROOT / "models/scaler.pkl"
SHANGHAI_SCALER = ROOT / "models/shanghai/scaler.pkl"
OUTPUT_DIR = ROOT / "data/processed/mixed"
MODELS_DIR = ROOT / "models/mixed"

LABEL_COLS = [f"BG_{t}min" for t in range(5, 125, 5)]
SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]


def load_pkl(path: Path) -> Any:
    with open(path, "rb") as f:
        return pickle.load(f)


def inverse_features(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    df = df.copy()
    df[SCALE_COLS] = scaler["model1_features"].inverse_transform(df[SCALE_COLS].values)
    return df


def inverse_bg(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    df = df.copy()
    flat = df[LABEL_COLS].values.reshape(-1, 1)
    df[LABEL_COLS] = scaler["bg_target"].inverse_transform(flat).reshape(-1, len(LABEL_COLS))
    return df


def inverse_all(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    return inverse_bg(inverse_features(df, scaler), scaler)


def normalize_features(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    df = df.copy()
    df[SCALE_COLS] = scaler["model1_features"].transform(df[SCALE_COLS].values)
    return df


def normalize_bg(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    df = df.copy()
    flat = df[LABEL_COLS].values.reshape(-1, 1)
    df[LABEL_COLS] = scaler["bg_target"].transform(flat).reshape(-1, len(LABEL_COLS))
    return df


def normalize_all(df: pd.DataFrame, scaler: Any) -> pd.DataFrame:
    return normalize_bg(normalize_features(df, scaler), scaler)


def main() -> int:
    print("[E2-Mixed 전처리]")

    for p in (SIM_PROCESSED / "train.csv", SHANGHAI_PROCESSED / "train.csv",
              SIM_SCALER, SHANGHAI_SCALER):
        if not p.exists():
            print(f"[ERROR] 파일 없음: {p}")
            return 1

    print("[Step 1] 각 스케일러 로드")
    sim_scaler = load_pkl(SIM_SCALER)
    sh_scaler = load_pkl(SHANGHAI_SCALER)
    print(f"  sim bg mean={sim_scaler['bg_target'].mean_[0]:.1f}, "
          f"shanghai bg mean={sh_scaler['bg_target'].mean_[0]:.1f}")

    print("[Step 2] 원본값 복원 (inverse transform)")
    sim_train_raw = inverse_all(pd.read_csv(SIM_PROCESSED / "train.csv"), sim_scaler)
    sim_val_raw = inverse_all(pd.read_csv(SIM_PROCESSED / "val.csv"), sim_scaler)

    sh_train_raw = inverse_all(pd.read_csv(SHANGHAI_PROCESSED / "train.csv"), sh_scaler)
    sh_val_raw = inverse_all(pd.read_csv(SHANGHAI_PROCESSED / "val.csv"), sh_scaler)
    sh_test_raw = inverse_all(pd.read_csv(SHANGHAI_PROCESSED / "test.csv"), sh_scaler)

    # user_id 컬럼 통일 (sim csv는 user_id 없음)
    for df in (sim_train_raw, sim_val_raw):
        if "user_id" not in df.columns:
            df.insert(0, "user_id", "sim")

    print(f"  sim train={len(sim_train_raw)}, val={len(sim_val_raw)}")
    print(f"  shanghai train={len(sh_train_raw)}, val={len(sh_val_raw)}, test={len(sh_test_raw)}")

    print("[Step 3] 통합 train 으로 새 scaler 피팅")
    combined_train_raw = pd.concat([sim_train_raw, sh_train_raw], ignore_index=True)
    combined_val_raw = pd.concat([sim_val_raw, sh_val_raw], ignore_index=True)

    mixed_feature_scaler = StandardScaler()
    mixed_bg_scaler = StandardScaler()
    mixed_profile_scaler = StandardScaler()

    mixed_feature_scaler.fit(combined_train_raw[SCALE_COLS].values)
    mixed_bg_scaler.fit(combined_train_raw[LABEL_COLS].values.reshape(-1, 1))

    profile_cols = ["weight_kg", "fasting_bg"]
    if all(c in combined_train_raw.columns for c in profile_cols):
        mixed_profile_scaler.fit(combined_train_raw[profile_cols].values)
    else:
        mixed_profile_scaler.fit(sim_train_raw[profile_cols].values)

    mixed_scaler = {
        "model1_features": mixed_feature_scaler,
        "bg_target": mixed_bg_scaler,
        "profile": mixed_profile_scaler,
    }

    print(f"  mixed bg mean={mixed_bg_scaler.mean_[0]:.1f}, std={mixed_bg_scaler.scale_[0]:.1f}")

    print("[Step 4] 정규화 적용")
    def norm(df: pd.DataFrame) -> pd.DataFrame:
        df = df.copy()
        df[SCALE_COLS] = mixed_feature_scaler.transform(df[SCALE_COLS].values)
        flat = df[LABEL_COLS].values.reshape(-1, 1)
        df[LABEL_COLS] = mixed_bg_scaler.transform(flat).reshape(-1, len(LABEL_COLS))
        return df

    train_norm = norm(combined_train_raw)
    val_norm = norm(combined_val_raw)
    test_norm = norm(sh_test_raw)  # 평가는 Shanghai test 만

    print("[Step 5] 저장")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    train_norm.to_csv(OUTPUT_DIR / "train.csv", index=False)
    val_norm.to_csv(OUTPUT_DIR / "val.csv", index=False)
    test_norm.to_csv(OUTPUT_DIR / "test.csv", index=False)
    print(f"  train={len(train_norm)}, val={len(val_norm)}, test={len(test_norm)}")

    with open(MODELS_DIR / "scaler.pkl", "wb") as f:
        pickle.dump(mixed_scaler, f)
    print(f"  scaler: {MODELS_DIR / 'scaler.pkl'}")

    print("[Step 6] 정규화 검증")
    for c in SCALE_COLS:
        print(f"  {c}: mean={train_norm[c].mean():+.3f}, std={train_norm[c].std():.3f}")
    bg_flat = train_norm[LABEL_COLS].values.flatten()
    print(f"  BG target: mean={bg_flat.mean():+.3f}, std={bg_flat.std():.3f}")

    print("\n[완료]")
    return 0


if __name__ == "__main__":
    os.chdir(ROOT)
    sys.exit(main())
