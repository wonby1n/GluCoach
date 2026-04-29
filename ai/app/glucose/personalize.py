"""환자별 개인화 fine-tuning.

base 모델 (lstm_meal.pt) 을 특정 환자 1명의 데이터로 추가 학습 → 그 환자
전용 모델 (lstm_meal_personalized_{user_id}.pt) 생성.

전제:
- base 모델 학습 완료 (ai/models/lstm_meal.pt)
- CSV 에 **user_id 컬럼 유지** (preprocess.py 가 drop 하지 않도록 추가 요청 필요)
- val 또는 test 환자 중 한 명 선택 (base 가 학습한 적 없는 환자가 의미 있음)

사용:
    python -m app.glucose.personalize \\
        --base models/lstm_meal.pt \\
        --csv data/processed/val.csv \\
        --user sim_0042

전략:
- 카테고리 임베딩 freeze (일반적 표현 보존)
- encoder + LSTM cell + head 만 fine-tune
- lr = base lr 의 1/10 (overfit 방지)
- 환자 데이터 70/30 split (finetune/val_finetune)
- base 모델 vs personalized 의 val_finetune RMSE 비교 출력
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

from app.glucose.constants import (
    DEFAULT_MODELS_DIR,
    DEFAULT_PROCESSED_DIR,
    LABEL_COLS,
    LABEL_STEPS,
    MEAL_CATEGORICAL_COLS,
    MEAL_CONTINUOUS_COLS,
)
from app.glucose.data_loader import load_scaler
from app.glucose.model import MealLSTMDecoder, load_torch_model, save_torch_model
from app.glucose.seed import set_seed


KEY_HORIZONS = [30, 60, 120]


class _PerUserMealDataset(Dataset):
    """단일 환자의 행만 담은 임시 Dataset."""

    def __init__(self, df: pd.DataFrame) -> None:
        self.x_continuous = df[MEAL_CONTINUOUS_COLS].to_numpy(dtype=np.float32)
        self.x_categorical = df[MEAL_CATEGORICAL_COLS].to_numpy(dtype=np.int64)
        self.y = df[LABEL_COLS].to_numpy(dtype=np.float32)

    def __len__(self) -> int:
        return len(self.y)

    def __getitem__(self, idx: int) -> Any:
        return (
            (
                torch.from_numpy(self.x_continuous[idx]),
                torch.from_numpy(self.x_categorical[idx]),
            ),
            torch.from_numpy(self.y[idx]),
        )


def _inverse_bg(y_norm: np.ndarray, bg_scaler: Any) -> np.ndarray:
    flat = y_norm.reshape(-1, 1)
    return bg_scaler.inverse_transform(flat).reshape(y_norm.shape)


def _evaluate(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
    bg_scaler: Any,
) -> np.ndarray:
    """val 셋 평가 → rmse_per_horizon (raw mg/dL)."""
    model.eval()
    preds: list[np.ndarray] = []
    targets: list[np.ndarray] = []
    with torch.no_grad():
        for (x_cont, x_cat), y in loader:
            x_cont = x_cont.to(device)
            x_cat = x_cat.to(device)
            preds.append(model(x_cont, x_cat).cpu().numpy())
            targets.append(y.numpy())
    if not preds:
        return np.zeros(24)
    y_pred_norm = np.concatenate(preds, axis=0)
    y_true_norm = np.concatenate(targets, axis=0)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
    y_true_raw = _inverse_bg(y_true_norm, bg_scaler)
    return np.sqrt(np.mean((y_true_raw - y_pred_raw) ** 2, axis=0))


def _print_horizon_summary(label: str, rmse: np.ndarray) -> None:
    parts = [f"RMSE_{h}={rmse[LABEL_STEPS.index(h)]:.2f}" for h in KEY_HORIZONS]
    print(f"  [{label}] " + ", ".join(parts))


def finetune_meal(
    base_model_path: Path,
    csv_with_user_id: Path,
    target_user_id: str,
    save_path: Path,
    epochs: int = 15,
    lr: float = 1e-4,
    finetune_ratio: float = 0.7,
    batch_size: int = 16,
    device: str = "cuda",
    seed: int = 42,
) -> None:
    device_t = torch.device(device if torch.cuda.is_available() else "cpu")
    print(f"  device: {device_t}")

    scaler = load_scaler()
    bg_scaler = scaler["bg_target"]

    # 1. CSV 에서 target user 필터
    df = pd.read_csv(csv_with_user_id)
    if "user_id" not in df.columns:
        raise RuntimeError(
            f"{csv_with_user_id} 에 user_id 컬럼 없음. "
            "preprocess.py 가 user_id 컬럼을 유지하도록 데이터 담당자에게 요청 필요."
        )
    df_user = df[df["user_id"] == target_user_id].reset_index(drop=True)
    if len(df_user) < 5:
        raise RuntimeError(
            f"환자 {target_user_id} 의 데이터가 너무 적음 ({len(df_user)} 건). "
            "개인화 의미 없음. 환자당 30+ 건 권장."
        )
    print(f"  환자 {target_user_id}: 총 {len(df_user)} 건")

    # 2. 70/30 split (random)
    rng = np.random.default_rng(seed)
    indices = rng.permutation(len(df_user))
    n_finetune = int(len(df_user) * finetune_ratio)
    df_ft = df_user.iloc[indices[:n_finetune]].reset_index(drop=True)
    df_val = df_user.iloc[indices[n_finetune:]].reset_index(drop=True)
    print(f"  finetune={len(df_ft)}, val={len(df_val)}")

    ft_loader = DataLoader(_PerUserMealDataset(df_ft), batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(_PerUserMealDataset(df_val), batch_size=batch_size, shuffle=False)

    # 3. base 모델 로드
    if not base_model_path.exists():
        raise RuntimeError(f"base 모델 없음: {base_model_path}. 먼저 train_base.py --model lstm")
    model, base_meta = load_torch_model(base_model_path, MealLSTMDecoder)
    model = model.to(device_t)

    # 4. base 모델로 val 평가 (비교 기준)
    base_rmse = _evaluate(model, val_loader, device_t, bg_scaler)
    _print_horizon_summary("base", base_rmse)

    # 5. embedding freeze (일반적 표현 보존)
    for emb in model.cat_embed.embeddings:
        for p in emb.parameters():
            p.requires_grad = False
    n_trainable = sum(p.numel() for p in model.parameters() if p.requires_grad)
    n_frozen = sum(p.numel() for p in model.parameters() if not p.requires_grad)
    print(f"  freeze: {n_frozen:,}, trainable: {n_trainable:,}")

    # 6. fine-tune
    optim = torch.optim.Adam(
        [p for p in model.parameters() if p.requires_grad], lr=lr
    )
    loss_fn = nn.MSELoss()

    best_rmse = float("inf")
    best_state = None
    for epoch in range(1, epochs + 1):
        t0 = time.time()
        model.train()
        train_loss = 0.0
        for (x_cont, x_cat), y in ft_loader:
            x_cont = x_cont.to(device_t)
            x_cat = x_cat.to(device_t)
            y = y.to(device_t)
            out = model(x_cont, x_cat)
            loss = loss_fn(out, y)
            optim.zero_grad()
            loss.backward()
            optim.step()
            train_loss += loss.item() * x_cont.size(0)
        train_loss /= len(df_ft)

        rmse = _evaluate(model, val_loader, device_t, bg_scaler)
        rmse_30 = rmse[LABEL_STEPS.index(30)]
        print(
            f"  epoch {epoch:>2}/{epochs}  loss={train_loss:.4f}  "
            f"val RMSE_30={rmse_30:.2f}  ({time.time()-t0:.1f}s)"
        )
        if rmse_30 < best_rmse:
            best_rmse = rmse_30
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)

    # 7. 결과 비교
    final_rmse = _evaluate(model, val_loader, device_t, bg_scaler)
    print()
    _print_horizon_summary("base", base_rmse)
    _print_horizon_summary("pers", final_rmse)

    delta_30 = float(base_rmse[LABEL_STEPS.index(30)] - final_rmse[LABEL_STEPS.index(30)])
    sign = "↓" if delta_30 > 0 else "↑"
    pct = 100 * delta_30 / base_rmse[LABEL_STEPS.index(30)]
    print(f"  Δ@30min = {abs(delta_30):.2f} mg/dL {sign}  ({pct:+.1f}%)")

    # 저장
    save_torch_model(
        model,
        save_path,
        meta={
            "model_class": "MealLSTMDecoder",
            "base_model": str(base_model_path),
            "target_user_id": target_user_id,
            "n_finetune": int(len(df_ft)),
            "n_val": int(len(df_val)),
            "epochs": epochs,
            "lr": lr,
            "base_val_rmse_per_horizon": base_rmse.tolist(),
            "personalized_val_rmse_per_horizon": final_rmse.tolist(),
            "improvement_30min_mg_dl": delta_30,
        },
    )
    print(f"\n  saved: {save_path}")


def main() -> None:
    set_seed()
    parser = argparse.ArgumentParser(description="환자별 개인화 fine-tuning")
    parser.add_argument("--base", default=f"{DEFAULT_MODELS_DIR}/lstm_meal.pt")
    parser.add_argument(
        "--csv",
        default=f"{DEFAULT_PROCESSED_DIR}/val.csv",
        help="환자 데이터 CSV (user_id 컬럼 필수)",
    )
    parser.add_argument("--user", required=True, help="대상 환자 user_id")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--lr", type=float, default=1e-4)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--out-dir", default=DEFAULT_MODELS_DIR)
    parser.add_argument("--device", default="cuda")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    save_path = out_dir / f"lstm_meal_personalized_{args.user}.pt"

    print(f"[Personalize] user={args.user}")
    finetune_meal(
        base_model_path=Path(args.base),
        csv_with_user_id=Path(args.csv),
        target_user_id=args.user,
        save_path=save_path,
        epochs=args.epochs,
        lr=args.lr,
        batch_size=args.batch_size,
        device=args.device,
    )


if __name__ == "__main__":
    main()
