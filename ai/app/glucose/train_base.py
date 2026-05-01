"""Model 1 학습 (Ridge / MLP / LSTM-decoder).

전제: 데이터 담당자가 다음을 채워둠
    data/processed/{train,val,test}.csv  (33 컬럼)
    models/scaler.pkl                    (dict)

실행:
    python -m app.glucose.train_base --model ridge
    python -m app.glucose.train_base --model mlp
    python -m app.glucose.train_base --model lstm
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader

from app.glucose.constants import (
    DEFAULT_MODELS_DIR,
    DEFAULT_PROCESSED_DIR,
    DEFAULT_SCALER_PATH,
    LABEL_STEPS,
)
from app.glucose.data_loader import MealDataset, get_dataloader, load_scaler
from app.glucose.model import (
    MealLSTMDecoder,
    MealMLP,
    MealRidge,
    save_torch_model,
)
from app.glucose.seed import set_seed


KEY_HORIZONS = [30, 60, 120]


# ─────────────────────────────────────────────────────────────────────
# 평가 (raw mg/dL 기준)
# ─────────────────────────────────────────────────────────────────────


def _inverse_bg(y_normalized: np.ndarray, bg_scaler: Any) -> np.ndarray:
    """[N, 24] 정규화 BG → raw mg/dL."""
    flat = y_normalized.reshape(-1, 1)
    raw = bg_scaler.inverse_transform(flat).reshape(y_normalized.shape)
    return raw


def _rmse_per_horizon(y_true_raw: np.ndarray, y_pred_raw: np.ndarray) -> np.ndarray:
    return np.sqrt(np.mean((y_true_raw - y_pred_raw) ** 2, axis=0))


def _print_horizon_table(rmse: np.ndarray, mae: np.ndarray) -> None:
    print(f"  {'horizon':>10} | {'RMSE':>8} | {'MAE':>8}")
    print(f"  {'-' * 10}-+-{'-' * 8}-+-{'-' * 8}")
    for i, h in enumerate(LABEL_STEPS):
        marker = " *" if h in KEY_HORIZONS else ""
        print(f"  {h:>7}min | {rmse[i]:>8.2f} | {mae[i]:>8.2f}{marker}")


# ─────────────────────────────────────────────────────────────────────
# Ridge
# ─────────────────────────────────────────────────────────────────────


def train_ridge(
    train_csv: Path,
    val_csv: Path,
    save_path: Path,
    alpha_candidates: list[float] | None = None,
    scaler_path: str | None = None,
) -> None:
    # 표준 sklearn 권장 범위: 로그 스케일 7개
    alpha_candidates = alpha_candidates or np.logspace(-3, 3, 7).tolist()
    scaler = load_scaler(scaler_path) if scaler_path else load_scaler()
    bg_scaler = scaler["bg_target"]

    train_ds = MealDataset(train_csv, return_categorical_separately=True)
    val_ds = MealDataset(val_csv, return_categorical_separately=True)

    x_cont_tr = train_ds.x_continuous
    x_cat_tr = train_ds.x_categorical
    y_tr = train_ds.y  # 정규화된 BG (학습 시 정규화 값 그대로 회귀)

    x_cont_val = val_ds.x_continuous
    x_cat_val = val_ds.x_categorical
    y_val_raw = _inverse_bg(val_ds.y, bg_scaler)

    best_rmse = float("inf")
    best_alpha = None
    best_model = None
    for alpha in alpha_candidates:
        model = MealRidge(alpha=alpha)
        model.fit(x_cont_tr, x_cat_tr, y_tr)
        y_pred_norm = model.predict(x_cont_val, x_cat_val)
        y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
        rmse_30 = _rmse_per_horizon(y_val_raw, y_pred_raw)[5]  # 30min 인덱스
        print(f"  alpha={alpha}: val RMSE_30min = {rmse_30:.2f} mg/dL")
        if rmse_30 < best_rmse:
            best_rmse = rmse_30
            best_alpha = alpha
            best_model = model

    print(f"\n  best alpha = {best_alpha}")
    y_pred_raw = _inverse_bg(best_model.predict(x_cont_val, x_cat_val), bg_scaler)
    rmse = _rmse_per_horizon(y_val_raw, y_pred_raw)
    mae = np.mean(np.abs(y_val_raw - y_pred_raw), axis=0)
    _print_horizon_table(rmse, mae)
    best_model.save(save_path)
    print(f"\n  saved: {save_path}")


# ─────────────────────────────────────────────────────────────────────
# Torch (MLP / LSTM-decoder)
# ─────────────────────────────────────────────────────────────────────


def _evaluate_torch(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
    bg_scaler: Any,
) -> tuple[float, np.ndarray, np.ndarray]:
    """val 셋 평가 → (raw RMSE_30min, rmse_per_horizon, mae_per_horizon)."""
    model.eval()
    preds: list[np.ndarray] = []
    targets: list[np.ndarray] = []
    with torch.no_grad():
        for (x_cont, x_cat), y in loader:
            x_cont = x_cont.to(device)
            x_cat = x_cat.to(device)
            out = model(x_cont, x_cat).cpu().numpy()
            preds.append(out)
            targets.append(y.numpy())
    y_pred_norm = np.concatenate(preds, axis=0)
    y_true_norm = np.concatenate(targets, axis=0)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
    y_true_raw = _inverse_bg(y_true_norm, bg_scaler)
    rmse = _rmse_per_horizon(y_true_raw, y_pred_raw)
    mae = np.mean(np.abs(y_true_raw - y_pred_raw), axis=0)
    return float(rmse[5]), rmse, mae  # rmse[5] = 30min


def train_torch_meal(
    model_class: type[nn.Module],
    train_csv: Path,
    val_csv: Path,
    save_path: Path,
    epochs: int = 50,
    batch_size: int = 64,
    lr: float = 1e-3,
    device: str = "cuda",
    early_stopping_patience: int = 5,
    scaler_path: str | None = None,
) -> None:
    device_t = torch.device(device if torch.cuda.is_available() else "cpu")
    print(f"  device: {device_t}")

    scaler = load_scaler(scaler_path) if scaler_path else load_scaler()
    bg_scaler = scaler["bg_target"]

    train_ds = MealDataset(train_csv, return_categorical_separately=True)
    val_ds = MealDataset(val_csv, return_categorical_separately=True)
    train_loader = get_dataloader(train_ds, batch_size=batch_size, shuffle=True)
    val_loader = get_dataloader(val_ds, batch_size=batch_size, shuffle=False)
    print(f"  train={len(train_ds)}, val={len(val_ds)}")

    model = model_class().to(device_t)
    optim = torch.optim.Adam(model.parameters(), lr=lr)
    loss_fn = nn.MSELoss()

    best_rmse = float("inf")
    patience = 0
    best_state = None
    epoch_times: list[float] = []

    for epoch in range(1, epochs + 1):
        t0 = time.time()
        model.train()
        train_loss = 0.0
        for (x_cont, x_cat), y in train_loader:
            x_cont, x_cat, y = x_cont.to(device_t), x_cat.to(device_t), y.to(device_t)
            out = model(x_cont, x_cat)
            loss = loss_fn(out, y)
            optim.zero_grad()
            loss.backward()
            optim.step()
            train_loss += loss.item() * x_cont.size(0)
        train_loss /= len(train_ds)

        rmse_30, rmse_all, _ = _evaluate_torch(model, val_loader, device_t, bg_scaler)
        elapsed = time.time() - t0
        epoch_times.append(elapsed)
        eta = (epochs - epoch) * (sum(epoch_times) / len(epoch_times))
        print(
            f"  epoch {epoch:>3}/{epochs}  train_loss={train_loss:.4f}  "
            f"val RMSE_30={rmse_30:.2f}  ({elapsed:.1f}s, ETA {eta/60:.1f}min)"
        )

        if rmse_30 < best_rmse:
            best_rmse = rmse_30
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
            patience = 0
        else:
            patience += 1
            if patience >= early_stopping_patience:
                print(f"  early stop @ epoch {epoch}")
                break

    if best_state is not None:
        model.load_state_dict(best_state)
    rmse_30, rmse_all, mae_all = _evaluate_torch(model, val_loader, device_t, bg_scaler)
    print(f"\n  best val RMSE_30min = {rmse_30:.2f} mg/dL")
    _print_horizon_table(rmse_all, mae_all)

    save_torch_model(
        model,
        save_path,
        meta={
            "model_class": model_class.__name__,
            "epochs": epochs,
            "batch_size": batch_size,
            "lr": lr,
            "best_val_rmse_30min": float(rmse_30),
            "val_rmse_per_horizon": rmse_all.tolist(),
            "val_mae_per_horizon": mae_all.tolist(),
        },
        scaler_path=scaler_path or DEFAULT_SCALER_PATH,
    )
    print(f"\n  saved: {save_path}")


# ─────────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────────


def main() -> None:
    set_seed()
    parser = argparse.ArgumentParser(description="Model 1 학습")
    parser.add_argument(
        "--model",
        choices=["ridge", "mlp", "lstm"],
        required=True,
        help="학습할 모델 종류",
    )
    parser.add_argument("--train", default=f"{DEFAULT_PROCESSED_DIR}/train.csv")
    parser.add_argument("--val", default=f"{DEFAULT_PROCESSED_DIR}/val.csv")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--out", default=DEFAULT_MODELS_DIR)
    parser.add_argument("--scaler", default=None, help="scaler.pkl 경로 (기본: models/scaler.pkl)")
    args = parser.parse_args()

    train_csv = Path(args.train)
    val_csv = Path(args.val)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"[Model 1: {args.model.upper()}]")

    if args.model == "ridge":
        train_ridge(train_csv, val_csv, out_dir / "ridge_meal.pkl", scaler_path=args.scaler)
    elif args.model == "mlp":
        train_torch_meal(
            MealMLP,
            train_csv,
            val_csv,
            out_dir / "mlp_meal.pt",
            epochs=args.epochs,
            batch_size=args.batch_size,
            lr=args.lr,
            device=args.device,
            scaler_path=args.scaler,
        )
    elif args.model == "lstm":
        train_torch_meal(
            MealLSTMDecoder,
            train_csv,
            val_csv,
            out_dir / "lstm_meal.pt",
            epochs=args.epochs,
            batch_size=args.batch_size,
            lr=args.lr,
            device=args.device,
            scaler_path=args.scaler,
        )


if __name__ == "__main__":
    main()
