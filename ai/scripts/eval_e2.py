"""E2 데이터 전략 비교 평가 스크립트.

시나리오:
  SimOnly      sim 학습 → sim test 평가
  SimEvalReal  sim 학습 → Shanghai test (sim scaler로 재정규화)
  RealOnly     Shanghai 학습 → Shanghai test 평가
  Mixed        sim+Shanghai 학습 → Shanghai test 평가

사용법:
    cd ai/
    python scripts/eval_e2.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.glucose.constants import LABEL_COLS, LABEL_STEPS, MEAL_CONTINUOUS_COLS, MEAL_CATEGORICAL_COLS
from app.glucose.data_loader import MealDataset, get_dataloader, load_scaler
from app.glucose.model import MealLSTMDecoder, load_torch_model

SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]


def _inverse_bg(y_norm: np.ndarray, bg_scaler: Any) -> np.ndarray:
    flat = y_norm.reshape(-1, 1)
    return bg_scaler.inverse_transform(flat).reshape(y_norm.shape)


def eval_lstm(model_path: Path, test_csv: Path, scaler_path: Path) -> dict[str, Any] | None:
    if not model_path.exists():
        print(f"  skip (모델 없음): {model_path}")
        return None
    if not test_csv.exists():
        print(f"  skip (데이터 없음): {test_csv}")
        return None

    scaler = load_scaler(scaler_path)
    bg_scaler = scaler["bg_target"]

    ds = MealDataset(test_csv, return_categorical_separately=True)
    loader = get_dataloader(ds, batch_size=64, shuffle=False)
    model, _ = load_torch_model(model_path, MealLSTMDecoder, scaler_path=scaler_path, strict_scaler=False)
    model.eval()

    preds: list[np.ndarray] = []
    with torch.no_grad():
        for (x_cont, x_cat), _ in loader:
            preds.append(model(x_cont, x_cat).numpy())
    y_pred_norm = np.concatenate(preds, axis=0)

    y_true_raw = _inverse_bg(ds.y, bg_scaler)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)

    rmse = np.sqrt(np.mean((y_true_raw - y_pred_raw) ** 2, axis=0))
    mae = np.mean(np.abs(y_true_raw - y_pred_raw), axis=0)
    return {"rmse": rmse, "mae": mae, "n": len(ds)}


def eval_sim_model_on_shanghai(
    model_path: Path,
    shanghai_test_csv: Path,
    sim_scaler_path: Path,
    shanghai_scaler_path: Path,
) -> dict[str, Any] | None:
    """sim 학습 모델을 Shanghai test에 적용 (sim scaler로 재정규화)."""
    if not model_path.exists():
        print(f"  skip (모델 없음): {model_path}")
        return None
    if not shanghai_test_csv.exists():
        print(f"  skip (데이터 없음): {shanghai_test_csv}")
        return None

    sim_scaler = load_scaler(sim_scaler_path)
    sh_scaler = load_scaler(shanghai_scaler_path)
    sim_bg = sim_scaler["bg_target"]
    sh_bg = sh_scaler["bg_target"]
    sim_feat = sim_scaler["model1_features"]
    sh_feat = sh_scaler["model1_features"]

    df = pd.read_csv(shanghai_test_csv)

    # Shanghai scaler로 역변환 → raw 값
    df[SCALE_COLS] = sh_feat.inverse_transform(df[SCALE_COLS].values)
    flat = df[LABEL_COLS].values.reshape(-1, 1)
    df[LABEL_COLS] = sh_bg.inverse_transform(flat).reshape(-1, len(LABEL_COLS))

    # sim scaler로 재정규화
    df[SCALE_COLS] = sim_feat.transform(df[SCALE_COLS].values)
    flat = df[LABEL_COLS].values.reshape(-1, 1)
    df[LABEL_COLS] = sim_bg.transform(flat).reshape(-1, len(LABEL_COLS))

    x_cont = df[MEAL_CONTINUOUS_COLS].to_numpy(dtype=np.float32)
    x_cat = df[MEAL_CATEGORICAL_COLS].to_numpy(dtype=np.int64)
    y_norm = df[LABEL_COLS].to_numpy(dtype=np.float32)

    model, _ = load_torch_model(model_path, MealLSTMDecoder, scaler_path=sim_scaler_path, strict_scaler=False)
    model.eval()

    preds: list[np.ndarray] = []
    bs = 64
    with torch.no_grad():
        for i in range(0, len(x_cont), bs):
            xc = torch.from_numpy(x_cont[i:i+bs])
            xk = torch.from_numpy(x_cat[i:i+bs])
            preds.append(model(xc, xk).numpy())
    y_pred_norm = np.concatenate(preds, axis=0)

    y_true_raw = _inverse_bg(y_norm, sim_bg)
    y_pred_raw = _inverse_bg(y_pred_norm, sim_bg)

    rmse = np.sqrt(np.mean((y_true_raw - y_pred_raw) ** 2, axis=0))
    mae = np.mean(np.abs(y_true_raw - y_pred_raw), axis=0)
    return {"rmse": rmse, "mae": mae, "n": len(df)}


def print_result(name: str, res: dict[str, Any] | None) -> None:
    if res is None:
        return
    i30 = LABEL_STEPS.index(30)
    i60 = LABEL_STEPS.index(60)
    i120 = LABEL_STEPS.index(120)
    print(f"  {name:<35s}  N={res['n']:>6}  "
          f"RMSE@30={res['rmse'][i30]:>6.2f}  "
          f"RMSE@60={res['rmse'][i60]:>6.2f}  "
          f"RMSE@120={res['rmse'][i120]:>6.2f}")


def main() -> int:
    os.chdir(ROOT)
    print("[E2 데이터 전략 비교]\n")
    print(f"  {'시나리오':<35s}  {'N':>6}  {'@30':>8}  {'@60':>8}  {'@120':>9}")
    print(f"  {'-'*35}  {'-'*6}  {'-'*8}  {'-'*8}  {'-'*9}")

    # SimOnly
    res = eval_lstm(
        ROOT / "models/lstm_meal.pt",
        ROOT / "data/processed/test.csv",
        ROOT / "models/scaler.pkl",
    )
    print_result("SimOnly (sim->sim test)", res)

    # SimEvalReal
    res = eval_sim_model_on_shanghai(
        ROOT / "models/lstm_meal.pt",
        ROOT / "data/processed/shanghai/test.csv",
        ROOT / "models/scaler.pkl",
        ROOT / "models/shanghai/scaler.pkl",
    )
    print_result("SimEvalReal (sim->shanghai test)", res)

    # RealOnly
    res = eval_lstm(
        ROOT / "models/shanghai/lstm_meal.pt",
        ROOT / "data/processed/shanghai/test.csv",
        ROOT / "models/shanghai/scaler.pkl",
    )
    print_result("RealOnly (shanghai->shanghai test)", res)

    # Mixed
    res = eval_lstm(
        ROOT / "models/mixed/lstm_meal.pt",
        ROOT / "data/processed/mixed/test.csv",
        ROOT / "models/mixed/scaler.pkl",
    )
    print_result("Mixed (sim+sh->shanghai test)", res)

    print(f"\n  임상 기준: RMSE@30 <=15 (우수), <=10 (임상급)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
