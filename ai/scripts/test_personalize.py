"""Shanghai 환자 데이터로 개인화 파인튜닝 효과 검증.

[읽기 전용 - 절대 수정하지 않는 파일]
  models/scaler.pkl                (sim scaler)
  models/shanghai/scaler.pkl      (Shanghai scaler)
  models/lstm_meal.pt             (base 모델)
  data/processed/shanghai/*.csv   (Shanghai 전처리 데이터)

[새로 생성 - 기존 파일과 완전 분리]
  models/personalize_test/lstm_meal_personalized_{user_id}.pt
  reports/personalize_test/results.csv
  reports/personalize_test/summary.txt

동작:
  1. Shanghai CSV 로드 (train + val + test 합산)
  2. Shanghai scaler 역변환 -> sim scaler 재정규화
  3. 환자별 시간순 70% fine-tune / 30% 평가
  4. base 모델 vs 개인화 모델 RMSE 비교 출력

사용법:
    cd ai/
    python scripts/test_personalize.py
    python scripts/test_personalize.py --min-samples 15 --epochs 30 --lr 3e-4
    python scripts/test_personalize.py --base-model models/shanghai/lstm_meal.pt
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.glucose.constants import (
    DEFAULT_SCALER_PATH,
    LABEL_COLS,
    LABEL_STEPS,
    MEAL_CATEGORICAL_COLS,
    MEAL_CONTINUOUS_COLS,
    MEAL_SCALE_COLS,
)
from app.glucose.data_loader import load_scaler
from app.glucose.model import MealLSTMDecoder, load_torch_model, save_torch_model

KEY_HORIZONS = [30, 60, 120]


# ─────────────────────────────────────────────────────────────────────
# scaler 재정규화
# ─────────────────────────────────────────────────────────────────────


def rescale_to_sim(df: pd.DataFrame, sh_scaler: dict, sim_scaler: dict) -> pd.DataFrame:
    """Shanghai scaler 정규화 -> raw -> sim scaler 정규화.

    sin/cos, 카테고리 컬럼은 건드리지 않음.
    """
    df = df.copy()

    # feature 4개 (carbs, current_glucose, fasting_bg, weight_kg)
    raw_feat = sh_scaler["model1_features"].inverse_transform(df[MEAL_SCALE_COLS].values)
    df[MEAL_SCALE_COLS] = sim_scaler["model1_features"].transform(raw_feat)

    # BG label 24개 (BG_5min ~ BG_120min)
    n = len(df)
    raw_bg = sh_scaler["bg_target"].inverse_transform(
        df[LABEL_COLS].values.reshape(-1, 1)
    ).reshape(n, len(LABEL_COLS))
    df[LABEL_COLS] = sim_scaler["bg_target"].transform(
        raw_bg.reshape(-1, 1)
    ).reshape(n, len(LABEL_COLS))

    return df


# ─────────────────────────────────────────────────────────────────────
# Dataset
# ─────────────────────────────────────────────────────────────────────


class _MealDF(Dataset):
    def __init__(self, df: pd.DataFrame) -> None:
        self.xc = df[MEAL_CONTINUOUS_COLS].to_numpy(dtype=np.float32)
        self.xk = df[MEAL_CATEGORICAL_COLS].to_numpy(dtype=np.int64)
        self.y = df[LABEL_COLS].to_numpy(dtype=np.float32)

    def __len__(self) -> int:
        return len(self.y)

    def __getitem__(self, idx: int) -> Any:
        return (
            (torch.from_numpy(self.xc[idx]), torch.from_numpy(self.xk[idx])),
            torch.from_numpy(self.y[idx]),
        )


# ─────────────────────────────────────────────────────────────────────
# 평가
# ─────────────────────────────────────────────────────────────────────


def _inverse_bg(y_norm: np.ndarray, bg_scaler: Any) -> np.ndarray:
    return bg_scaler.inverse_transform(y_norm.reshape(-1, 1)).reshape(y_norm.shape)


def evaluate(
    model: nn.Module,
    df: pd.DataFrame,
    bg_scaler: Any,
    device: torch.device,
) -> dict[int, float]:
    """RMSE per horizon (raw mg/dL). 반환: {30: x.xx, 60: x.xx, 120: x.xx}"""
    loader = DataLoader(_MealDF(df), batch_size=64, shuffle=False)
    model.eval()
    preds, targets = [], []
    with torch.no_grad():
        for (xc, xk), y in loader:
            preds.append(model(xc.to(device), xk.to(device)).cpu().numpy())
            targets.append(y.numpy())
    y_pred_raw = _inverse_bg(np.concatenate(preds), bg_scaler)
    y_true_raw = _inverse_bg(np.concatenate(targets), bg_scaler)
    rmse_all = np.sqrt(np.mean((y_true_raw - y_pred_raw) ** 2, axis=0))
    return {h: float(rmse_all[LABEL_STEPS.index(h)]) for h in KEY_HORIZONS}


# ─────────────────────────────────────────────────────────────────────
# 환자 1명 파인튜닝
# ─────────────────────────────────────────────────────────────────────


def finetune_one(
    df_train: pd.DataFrame,
    df_test: pd.DataFrame,
    base_model_path: Path,
    save_path: Path,
    bg_scaler: Any,
    device: torch.device,
    epochs: int,
    lr: float,
    batch_size: int = 16,
) -> dict[str, Any]:
    """base 평가 -> fine-tune -> 개인화 평가. 개선 시에만 save_path 에 저장."""

    # base 모델 로드 + base RMSE (scaler 버전 불일치 경고 무시)
    model, _ = load_torch_model(base_model_path, MealLSTMDecoder, strict_scaler=False)
    model = model.to(device)
    base_rmse = evaluate(model, df_test, bg_scaler, device)

    # 임베딩 freeze (1인 데이터로 임베딩 업데이트 시 과적합 위험)
    for emb in model.cat_embed.embeddings:
        for p in emb.parameters():
            p.requires_grad = False

    # fine-tune
    loader = DataLoader(_MealDF(df_train), batch_size=batch_size, shuffle=True)
    optim = torch.optim.Adam(
        [p for p in model.parameters() if p.requires_grad], lr=lr
    )
    loss_fn = nn.MSELoss()

    best_rmse_30 = float("inf")
    best_state: dict | None = None

    for _ in range(epochs):
        model.train()
        for (xc, xk), y in loader:
            out = model(xc.to(device), xk.to(device))
            loss = loss_fn(out, y.to(device))
            optim.zero_grad()
            loss.backward()
            optim.step()

        rmse_val = evaluate(model, df_test, bg_scaler, device)
        if rmse_val[30] < best_rmse_30:
            best_rmse_30 = rmse_val[30]
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state:
        model.load_state_dict(best_state)

    pers_rmse = evaluate(model, df_test, bg_scaler, device)
    delta_30 = base_rmse[30] - pers_rmse[30]
    pct = 100.0 * delta_30 / base_rmse[30] if base_rmse[30] > 0 else 0.0

    status = "personalized" if delta_30 > 0 else "rejected"
    if status == "personalized":
        save_torch_model(
            model,
            save_path,
            meta={
                "model_class": "MealLSTMDecoder",
                "base_model": str(base_model_path),
                "epochs": epochs,
                "lr": lr,
                "base_rmse_30": base_rmse[30],
                "personalized_rmse_30": pers_rmse[30],
                "improvement_pct": pct,
            },
            scaler_path=DEFAULT_SCALER_PATH,
        )

    return {
        "status": status,
        "n_train": len(df_train),
        "n_test": len(df_test),
        "base_30": base_rmse[30],
        "base_60": base_rmse[60],
        "base_120": base_rmse[120],
        "pers_30": pers_rmse[30],
        "pers_60": pers_rmse[60],
        "pers_120": pers_rmse[120],
        "delta_30": delta_30,
        "improvement_pct": pct,
    }


# ─────────────────────────────────────────────────────────────────────
# main
# ─────────────────────────────────────────────────────────────────────


def main() -> int:
    os.chdir(ROOT)

    parser = argparse.ArgumentParser(description="개인화 파인튜닝 효과 검증")
    parser.add_argument("--min-samples", type=int, default=12,
                        help="환자당 최소 식사 기록 수 (기본 12)")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--train-ratio", type=float, default=0.7,
                        help="시간순 앞 N%% fine-tune, 뒤 30%% 평가")
    parser.add_argument("--base-model", default="models/lstm_meal.pt",
                        help="베이스 모델 경로 (기본: sim 학습 LSTM)")
    parser.add_argument("--out-dir", default="models/personalize_test",
                        help="테스트용 개인화 모델 저장 위치 (models/ 와 분리)")
    parser.add_argument("--report-dir", default="reports/personalize_test",
                        help="결과 CSV / summary 저장 위치")
    args = parser.parse_args()

    base_path = ROOT / args.base_model
    out_dir   = ROOT / args.out_dir
    rep_dir   = ROOT / args.report_dir

    # 경로 확인
    if not base_path.exists():
        print(f"[ERROR] base 모델 없음: {base_path}")
        return 1
    sim_scaler_path   = ROOT / "models/scaler.pkl"
    sh_scaler_path    = ROOT / "models/shanghai/scaler.pkl"
    sh_data_dir       = ROOT / "data/processed/shanghai"
    if not sim_scaler_path.exists():
        print(f"[ERROR] sim scaler 없음: {sim_scaler_path}")
        return 1
    if not sh_scaler_path.exists():
        print(f"[ERROR] Shanghai scaler 없음: {sh_scaler_path}")
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)
    rep_dir.mkdir(parents=True, exist_ok=True)

    print("[개인화 파인튜닝 테스트]")
    print(f"  base model   : {base_path}")
    print(f"  epochs={args.epochs}, lr={args.lr}, min_samples={args.min_samples}")
    print(f"  [READ-ONLY]  : models/scaler.pkl, models/shanghai/scaler.pkl, {base_path.name}")
    print(f"  [NEW WRITE]  : {out_dir}  (기존 models/ 와 완전 분리)")
    print(f"  [NEW WRITE]  : {rep_dir}")
    print()

    # scaler 로드
    sim_scaler = load_scaler(sim_scaler_path)
    sh_scaler  = load_scaler(sh_scaler_path)
    bg_scaler  = sim_scaler["bg_target"]
    device     = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"  device: {device}\n")

    # Shanghai 데이터 로드 (train + val + test 합산)
    dfs = []
    for split in ("train", "val", "test"):
        p = sh_data_dir / f"{split}.csv"
        if p.exists():
            dfs.append(pd.read_csv(p))
        else:
            print(f"  [SKIP] {p.name} 없음")
    if not dfs:
        print("[ERROR] Shanghai 전처리 데이터 없음. preprocess_shanghai.py 먼저 실행.")
        return 1

    df_all = pd.concat(dfs, ignore_index=True)
    print(f"  Shanghai 전체: {len(df_all)}건 / {df_all['user_id'].nunique()}명\n")

    # scaler 재정규화: Shanghai -> sim
    df_all = rescale_to_sim(df_all, sh_scaler, sim_scaler)

    # 환자별 파인튜닝
    results: list[dict] = []
    uids = sorted(df_all["user_id"].unique())

    header = (f"  {'user_id':<22} {'N_train':>7} {'N_test':>6} "
              f"{'base@30':>8} {'pers@30':>8} {'delta@30':>9} {'상태':>12}")
    sep    = "  " + "-" * (len(header) - 2)
    print(header)
    print(sep)

    for uid in uids:
        df_u = df_all[df_all["user_id"] == uid].reset_index(drop=True)
        if len(df_u) < args.min_samples:
            continue

        n_train = max(int(len(df_u) * args.train_ratio), 1)
        df_train = df_u.iloc[:n_train]
        df_test  = df_u.iloc[n_train:]
        if len(df_test) < 3:
            continue

        save_path = out_dir / f"lstm_meal_personalized_{uid}.pt"
        try:
            res = finetune_one(
                df_train, df_test, base_path, save_path, bg_scaler, device,
                epochs=args.epochs, lr=args.lr,
            )
        except Exception as e:
            print(f"  {str(uid):<22}  [ERROR] {e}")
            continue

        res["user_id"] = str(uid)
        results.append(res)

        delta_str = f"{res['delta_30']:+.2f}"
        print(f"  {str(uid):<22} {res['n_train']:>7} {res['n_test']:>6} "
              f"{res['base_30']:>8.2f} {res['pers_30']:>8.2f} "
              f"{delta_str:>9} {res['status']:>12}")

    print(sep)

    if not results:
        print("\n  결과 없음 - min_samples 조건을 충족하는 환자가 없습니다.")
        return 1

    df_res = pd.DataFrame(results)
    improved = df_res[df_res["delta_30"] > 0]
    n_total  = len(df_res)
    n_impr   = len(improved)

    print(f"\n  총 {n_total}명 평가  |  개선: {n_impr}명 ({100*n_impr/n_total:.0f}%)  |  미개선: {n_total-n_impr}명")
    print(f"  평균 base RMSE@30 : {df_res['base_30'].mean():.2f} mg/dL")
    print(f"  평균 pers RMSE@30 : {df_res['pers_30'].mean():.2f} mg/dL")
    print(f"  평균 delta RMSE@30: {df_res['delta_30'].mean():+.2f} mg/dL")
    if n_impr > 0:
        print(f"  개선 환자만 평균  : {improved['delta_30'].mean():+.2f} mg/dL "
              f"({improved['improvement_pct'].mean():.1f}% 개선)")

    # 결과 저장
    csv_path     = rep_dir / "results.csv"
    summary_path = rep_dir / "summary.txt"

    df_res[[
        "user_id", "status", "n_train", "n_test",
        "base_30", "base_60", "base_120",
        "pers_30", "pers_60", "pers_120",
        "delta_30", "improvement_pct",
    ]].to_csv(csv_path, index=False)

    with open(summary_path, "w", encoding="utf-8") as f:
        f.write(f"개인화 파인튜닝 테스트 결과\n")
        f.write(f"base model  : {base_path}\n")
        f.write(f"epochs={args.epochs}, lr={args.lr}, min_samples={args.min_samples}\n\n")
        f.write(f"총 {n_total}명 평가\n")
        f.write(f"개선: {n_impr}명 ({100*n_impr/n_total:.0f}%)\n\n")
        f.write(f"평균 base RMSE@30 : {df_res['base_30'].mean():.2f} mg/dL\n")
        f.write(f"평균 pers RMSE@30 : {df_res['pers_30'].mean():.2f} mg/dL\n")
        f.write(f"평균 delta RMSE@30: {df_res['delta_30'].mean():+.2f} mg/dL\n")
        if n_impr > 0:
            f.write(f"개선 환자 평균    : {improved['delta_30'].mean():+.2f} mg/dL "
                    f"({improved['improvement_pct'].mean():.1f}%)\n")

    print(f"\n  결과 CSV : {csv_path}")
    print(f"  요약     : {summary_path}")
    print(f"  모델     : {out_dir}/lstm_meal_personalized_*.pt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
