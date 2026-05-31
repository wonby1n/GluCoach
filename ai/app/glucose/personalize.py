"""환자별 개인화 fine-tuning - ShanghaiLSTM 기반.

base 모델(ShanghaiLSTM coef=1.5)을 특정 환자의 실측 데이터로 추가 학습.
base 보다 RMSE@30 개선 없으면 자동 폐기.
"""
from __future__ import annotations

import math
import pickle
import time
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

from app.glucose.shanghai_lstm import ShanghaiLSTM, compute_prior_delta_row

from app.glucose import config as _cfg
from app.glucose.constants import LABEL_STEPS
from app.glucose.seed import set_seed

KEY_HORIZONS = [30, 60, 120]
DTYPE_MAP = {"T1D": 1, "T2D": 2, "Normal": 0, "T1DM": 1, "T2DM": 2}

BASE_SCALER_PATH = Path(_cfg.MODELS_DIR) / "lstm_t2dm_scaler_coef15.pkl"


# ─────────────────────────────────────────────────────────────────────
# 파일 정리
# ─────────────────────────────────────────────────────────────────────


def cleanup_old_personalized_models(
    models_dir: str = _cfg.MODELS_DIR,
    max_age_days: int = _cfg.PERSONALIZED_MAX_AGE_DAYS,
) -> list[str]:
    removed: list[str] = []
    dir_path = Path(models_dir)
    if not dir_path.exists():
        return removed
    cutoff = time.time() - max_age_days * 86400
    for pt_file in dir_path.glob("lstm_meal_personalized_*.pt"):
        if pt_file.stat().st_mtime < cutoff:
            pt_file.unlink()
            removed.append(pt_file.name)
    if removed:
        print(f"  [cleanup] 삭제된 개인화 모델: {removed}")
    return removed


# ─────────────────────────────────────────────────────────────────────
# 평가 헬퍼
# ─────────────────────────────────────────────────────────────────────


def _evaluate(
    model: ShanghaiLSTM,
    X: np.ndarray,
    y_norm: np.ndarray,
    prior: np.ndarray,
    pre_bg: np.ndarray,
    bg_scaler: Any,
    device: torch.device,
) -> np.ndarray:
    """val 셋 평가 → rmse_per_horizon [24] (raw mg/dL)."""
    model.eval()
    with torch.no_grad():
        y_pred_norm = model(torch.from_numpy(X).to(device)).cpu().numpy()

    def _to_abs(y_n: np.ndarray) -> np.ndarray:
        y_res = bg_scaler.inverse_transform(y_n.reshape(-1, 1)).reshape(-1, 24)
        return y_res + prior + pre_bg.reshape(-1, 1)

    y_pred_abs = _to_abs(y_pred_norm)
    y_true_abs = _to_abs(y_norm)
    return np.sqrt(np.mean((y_true_abs - y_pred_abs) ** 2, axis=0))


def _print_horizon_summary(label: str, rmse: np.ndarray) -> None:
    parts = [f"RMSE_{h}={rmse[LABEL_STEPS.index(h)]:.2f}" for h in KEY_HORIZONS]
    print(f"  [{label}] " + ", ".join(parts))


# ─────────────────────────────────────────────────────────────────────
# 핵심 학습 + 자동 폐기
# ─────────────────────────────────────────────────────────────────────


def _train_eval_save_or_reject(
    X: np.ndarray,
    y_norm: np.ndarray,
    prior: np.ndarray,
    pre_bg: np.ndarray,
    user_id: str,
    base_model_path: Path,
    save_path: Path,
    feat_scaler: Any,
    bg_scaler: Any,
    n_features: int,
    dtype_idx: int,
    epochs: int = 30,
    lr: float = 3e-4,
    finetune_ratio: float = 0.7,
    batch_size: int = 16,
    device_str: str = "cuda",
) -> dict[str, Any]:
    """X: scaled [N, 11], y_norm: normalized residual [N, 24]."""
    device = torch.device(device_str if torch.cuda.is_available() else "cpu")
    n = len(X)
    if n < 10:
        raise ValueError(f"데이터 너무 적음 ({n}건). 최소 10개 필요.")
    print(f"  환자 {user_id}: 총 {n}건  device: {device}")

    n_ft = int(n * finetune_ratio)
    X_ft = X[:n_ft];       y_ft = y_norm[:n_ft]
    X_val = X[n_ft:];      y_val = y_norm[n_ft:]
    prior_val = prior[n_ft:]; pre_bg_val = pre_bg[n_ft:]
    print(f"  finetune={n_ft}, val={n - n_ft}")

    loader = DataLoader(
        TensorDataset(torch.from_numpy(X_ft), torch.from_numpy(y_ft)),
        batch_size=batch_size, shuffle=True,
    )

    # 베이스 모델 로드
    if not base_model_path.exists():
        raise RuntimeError(f"base 모델 없음: {base_model_path}")
    model = ShanghaiLSTM(n_features=n_features, dtype_idx=dtype_idx)
    model.load_state_dict(
        torch.load(base_model_path, map_location="cpu", weights_only=True)
    )
    model = model.to(device)

    # 베이스 평가
    base_rmse = _evaluate(model, X_val, y_val, prior_val, pre_bg_val, bg_scaler, device)
    base_rmse_30 = float(base_rmse[LABEL_STEPS.index(30)])
    _print_horizon_summary("base", base_rmse)

    # dtype 임베딩 freeze
    for p in model.dtype_embed.parameters():
        p.requires_grad = False

    # fine-tune
    optim = torch.optim.Adam([p for p in model.parameters() if p.requires_grad], lr=lr)
    loss_fn = nn.MSELoss()
    best_rmse_30 = float("inf")
    best_state = None

    for epoch in range(1, epochs + 1):
        t0 = time.time()
        model.train()
        train_loss = 0.0
        for xb, yb in loader:
            xb, yb = xb.to(device), yb.to(device)
            out = model(xb)
            loss = loss_fn(out, yb)
            optim.zero_grad()
            loss.backward()
            optim.step()
            train_loss += loss.item() * xb.size(0)
        train_loss /= n_ft
        rmse = _evaluate(model, X_val, y_val, prior_val, pre_bg_val, bg_scaler, device)
        rmse_30 = float(rmse[LABEL_STEPS.index(30)])
        print(
            f"  epoch {epoch:>2}/{epochs}  loss={train_loss:.4f}  "
            f"val RMSE_30={rmse_30:.2f}  ({time.time()-t0:.1f}s)"
        )
        if rmse_30 < best_rmse_30:
            best_rmse_30 = rmse_30
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)

    final_rmse = _evaluate(model, X_val, y_val, prior_val, pre_bg_val, bg_scaler, device)
    final_rmse_30 = float(final_rmse[LABEL_STEPS.index(30)])
    delta_30 = base_rmse_30 - final_rmse_30
    pct = 100 * delta_30 / base_rmse_30 if base_rmse_30 > 0 else 0.0

    print()
    _print_horizon_summary("base", base_rmse)
    _print_horizon_summary("pers", final_rmse)
    print(f"  Δ@30min = {abs(delta_30):.2f} mg/dL {'↓' if delta_30 > 0 else '↑'}  ({pct:+.1f}%)")

    if delta_30 <= 0:
        kept = save_path.exists()
        print(
            f"\n  [REJECTED] 개선 없음. 새 모델 저장 안 함."
            + (f" 기존 개인화 모델 유지." if kept else " base 그대로 사용.")
        )
        return {
            "status": "rejected",
            "n_samples": n,
            "base_rmse_30min": base_rmse_30,
            "personalized_rmse_30min": final_rmse_30,
            "improvement_percent": float(pct),
            "save_path": None,
        }

    torch.save(model.state_dict(), save_path)
    print(f"\n  [OK] saved: {save_path}")
    return {
        "status": "personalized",
        "n_samples": n,
        "base_rmse_30min": base_rmse_30,
        "personalized_rmse_30min": final_rmse_30,
        "improvement_percent": float(pct),
        "save_path": str(save_path),
    }


# ─────────────────────────────────────────────────────────────────────
# 진입점: API (raw history dict 받아서 처리)
# ─────────────────────────────────────────────────────────────────────


def finetune_from_history(
    user_id: str,
    history: list[dict[str, Any]],
    user_profile: dict[str, Any],
    base_model_path: Path,
    save_path: Path,
    epochs: int = 30,
    lr: float = 3e-4,
    finetune_ratio: float = 0.7,
    batch_size: int = 16,
    device: str = "cuda",
    seed: int = 42,
) -> dict[str, Any]:
    """API 용. raw history list → ShanghaiLSTM fine-tuning.

    Args:
        history: list of {carbs, protein_g, fat_g, fiber_g, kcal,
                          meal_time_iso, current_glucose, bg_curve: [24]}
        user_profile: {diabetes_type, ...}
    """
    set_seed(seed)

    if not BASE_SCALER_PATH.exists():
        raise RuntimeError(f"ShanghaiLSTM scaler 없음: {BASE_SCALER_PATH}")
    with open(BASE_SCALER_PATH, "rb") as f:
        scalers = pickle.load(f)
    feat_scaler = scalers["feature"]
    bg_scaler   = scalers["bg_target"]
    carb_coef   = scalers.get("carb_coef", 1.5)
    n_features  = scalers.get("n_features", 11)
    dtype_idx   = scalers.get("dtype_idx", 8)

    dtype_int = DTYPE_MAP.get(str(user_profile.get("diabetes_type", "T2D")), 2)

    # 시간순 정렬 후 최근 N개
    history_sorted = sorted(history, key=lambda x: x["meal_time_iso"])
    history_sorted = history_sorted[-_cfg.FINETUNE_RECENT_N:]

    rows_x: list[np.ndarray] = []
    rows_y: list[np.ndarray] = []
    rows_prior: list[np.ndarray] = []
    rows_pre: list[float] = []

    for item in history_sorted:
        carbs      = float(item["carbs"])
        protein    = float(item.get("protein_g", 0.0))
        fat        = float(item.get("fat_g", 0.0))
        fiber      = float(item.get("fiber_g", 0.0))
        kcal       = float(item["kcal"]) if item.get("kcal") else carbs * 4 + protein * 4 + fat * 9
        pre_glucose = float(item["current_glucose"])
        bg_curve   = np.array(item["bg_curve"], dtype=np.float32)  # 24개 절댓값

        dt   = datetime.fromisoformat(item["meal_time_iso"].replace("Z", "+00:00"))
        hour = dt.hour + dt.minute / 60.0
        hour_sin = math.sin(2 * math.pi * hour / 24.0)
        hour_cos = math.cos(2 * math.pi * hour / 24.0)

        total       = carbs + protein + fat + 1e-6
        carb_ratio  = carbs / total
        protein_fat = protein + fat

        x_raw = np.array([
            carbs, protein, fat, fiber, kcal,
            pre_glucose, hour_sin, hour_cos,
            float(dtype_int), carb_ratio, protein_fat,
        ], dtype=np.float32)

        # 실제 delta → residual → normalize
        delta    = bg_curve - pre_glucose                                # [24]
        prior    = compute_prior_delta_row(carbs, protein, fat, fiber, pre_glucose, carb_coef)
        residual = delta - prior                                          # [24]
        y_norm   = bg_scaler.transform(residual.reshape(-1, 1)).flatten().astype(np.float32)

        rows_x.append(x_raw)
        rows_y.append(y_norm)
        rows_prior.append(prior)
        rows_pre.append(pre_glucose)

    X_raw  = np.stack(rows_x)             # [N, 11]
    y_norm = np.stack(rows_y)             # [N, 24]
    prior  = np.stack(rows_prior)         # [N, 24]
    pre_bg = np.array(rows_pre, dtype=np.float32)  # [N]

    X = feat_scaler.transform(X_raw).astype(np.float32)

    return _train_eval_save_or_reject(
        X=X, y_norm=y_norm, prior=prior, pre_bg=pre_bg,
        user_id=user_id,
        base_model_path=base_model_path,
        save_path=save_path,
        feat_scaler=feat_scaler,
        bg_scaler=bg_scaler,
        n_features=n_features,
        dtype_idx=dtype_idx,
        epochs=epochs,
        lr=lr,
        finetune_ratio=finetune_ratio,
        batch_size=batch_size,
        device_str=device,
    )
