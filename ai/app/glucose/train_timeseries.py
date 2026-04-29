"""Model 2 학습 (NowLSTM, 시계열 forecasting).

전제: 데이터 담당자가 다음을 채워둠
    data/processed/timeseries/{train,val,test}.npz
    models/scaler.pkl  (bg_target 키 필요)

실행:
    python -m app.glucose.train_timeseries
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
    DEFAULT_TIMESERIES_DIR,
    LABEL_STEPS,
)
from app.glucose.data_loader import TimeseriesDataset, get_dataloader, load_scaler
from app.glucose.model import NowLSTM, save_torch_model
from app.glucose.seed import set_seed


KEY_HORIZONS = [30, 60, 120]


def _inverse_bg(y_normalized: np.ndarray, bg_scaler: Any) -> np.ndarray:
    flat = y_normalized.reshape(-1, 1)
    return bg_scaler.inverse_transform(flat).reshape(y_normalized.shape)


def _rmse_per_horizon(y_true: np.ndarray, y_pred: np.ndarray) -> np.ndarray:
    return np.sqrt(np.mean((y_true - y_pred) ** 2, axis=0))


def _print_horizon_table(rmse: np.ndarray, mae: np.ndarray) -> None:
    print(f"  {'horizon':>10} | {'RMSE':>8} | {'MAE':>8}")
    print(f"  {'-' * 10}-+-{'-' * 8}-+-{'-' * 8}")
    for i, h in enumerate(LABEL_STEPS):
        marker = " ⭐" if h in KEY_HORIZONS else ""
        print(f"  {h:>7}min | {rmse[i]:>8.2f} | {mae[i]:>8.2f}{marker}")


def _evaluate(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
    bg_scaler: Any,
) -> tuple[float, np.ndarray, np.ndarray]:
    model.eval()
    preds: list[np.ndarray] = []
    targets: list[np.ndarray] = []
    with torch.no_grad():
        for (x_seq, x_profile), y in loader:
            x_seq = x_seq.to(device)
            x_profile = x_profile.to(device)
            out = model(x_seq, x_profile).cpu().numpy()
            preds.append(out)
            targets.append(y.numpy())
    y_pred_norm = np.concatenate(preds, axis=0)
    y_true_norm = np.concatenate(targets, axis=0)
    y_pred_raw = _inverse_bg(y_pred_norm, bg_scaler)
    y_true_raw = _inverse_bg(y_true_norm, bg_scaler)
    rmse = _rmse_per_horizon(y_true_raw, y_pred_raw)
    mae = np.mean(np.abs(y_true_raw - y_pred_raw), axis=0)
    return float(rmse[5]), rmse, mae


def train_now_lstm(
    train_npz: Path,
    val_npz: Path,
    save_path: Path,
    epochs: int = 50,
    batch_size: int = 64,
    lr: float = 1e-3,
    device: str = "cuda",
    early_stopping_patience: int = 5,
) -> None:
    device_t = torch.device(device if torch.cuda.is_available() else "cpu")
    print(f"  device: {device_t}")

    scaler = load_scaler()
    bg_scaler = scaler["bg_target"]

    train_ds = TimeseriesDataset(train_npz)
    val_ds = TimeseriesDataset(val_npz)
    train_loader = get_dataloader(train_ds, batch_size=batch_size, shuffle=True)
    val_loader = get_dataloader(val_ds, batch_size=batch_size, shuffle=False)
    print(f"  train={len(train_ds)}, val={len(val_ds)}")

    model = NowLSTM().to(device_t)
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
        for (x_seq, x_profile), y in train_loader:
            x_seq = x_seq.to(device_t)
            x_profile = x_profile.to(device_t)
            y = y.to(device_t)
            out = model(x_seq, x_profile)
            loss = loss_fn(out, y)
            optim.zero_grad()
            loss.backward()
            optim.step()
            train_loss += loss.item() * x_seq.size(0)
        train_loss /= len(train_ds)

        rmse_30, _, _ = _evaluate(model, val_loader, device_t, bg_scaler)
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
    rmse_30, rmse_all, mae_all = _evaluate(model, val_loader, device_t, bg_scaler)
    print(f"\n  best val RMSE_30min = {rmse_30:.2f} mg/dL")
    _print_horizon_table(rmse_all, mae_all)

    save_torch_model(
        model,
        save_path,
        meta={
            "model_class": "NowLSTM",
            "epochs": epochs,
            "batch_size": batch_size,
            "lr": lr,
            "best_val_rmse_30min": float(rmse_30),
            "val_rmse_per_horizon": rmse_all.tolist(),
            "val_mae_per_horizon": mae_all.tolist(),
        },
    )
    print(f"\n  saved: {save_path}")


def main() -> None:
    set_seed()
    parser = argparse.ArgumentParser(description="Model 2 학습 (NowLSTM)")
    parser.add_argument("--train", default=f"{DEFAULT_TIMESERIES_DIR}/train.npz")
    parser.add_argument("--val", default=f"{DEFAULT_TIMESERIES_DIR}/val.npz")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--device", default="cuda")
    parser.add_argument("--out", default=DEFAULT_MODELS_DIR)
    args = parser.parse_args()

    print("[Model 2: NowLSTM]")
    train_now_lstm(
        Path(args.train),
        Path(args.val),
        Path(args.out) / "lstm_now.pt",
        epochs=args.epochs,
        batch_size=args.batch_size,
        lr=args.lr,
        device=args.device,
    )


if __name__ == "__main__":
    main()
