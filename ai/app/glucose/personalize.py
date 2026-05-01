"""환자별 개인화 fine-tuning.

base 모델 (lstm_meal.pt) 을 특정 환자 1명의 데이터로 추가 학습 → 그 환자
전용 모델 (lstm_meal_personalized_{user_id}.pt) 생성.

진입점 두 개:
- `finetune_meal(...)`: CLI 용, train.csv/val.csv 의 user_id 컬럼으로 필터
- `finetune_from_history(...)`: API 용, raw history dict 받아서 처리

자동 폐기 로직: personalized 가 base 보다 RMSE@30 안 좋으면 모델 저장 X.
"""

from __future__ import annotations

import argparse
import math
import time
from datetime import datetime
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

from app.glucose.constants import (
    ACTIVITY_MAP,
    DEFAULT_MODELS_DIR,
    DEFAULT_PROCESSED_DIR,
    DIABETES_TYPE_MAP,
    LABEL_COLS,
    LABEL_STEPS,
    MEAL_CATEGORICAL_COLS,
    MEAL_CONTINUOUS_COLS,
    MEAL_PATTERN_MAP,
)
from app.glucose.data_loader import load_scaler
from app.glucose.model import MealLSTMDecoder, load_torch_model, save_torch_model
from app.glucose.seed import set_seed


_PERSONALIZED_MAX_AGE_DAYS = 90
KEY_HORIZONS = [30, 60, 120]


# ─────────────────────────────────────────────────────────────────────
# 파일 정리
# ─────────────────────────────────────────────────────────────────────


def cleanup_old_personalized_models(
    models_dir: str = DEFAULT_MODELS_DIR,
    max_age_days: int = _PERSONALIZED_MAX_AGE_DAYS,
) -> list[str]:
    """마지막 수정 후 max_age_days 이상 지난 개인화 모델 파일을 삭제한다."""
    removed: list[str] = []
    cutoff = time.time() - max_age_days * 86400
    for pt_file in Path(models_dir).glob("lstm_meal_personalized_*.pt"):
        if pt_file.stat().st_mtime < cutoff:
            meta_file = pt_file.with_suffix(pt_file.suffix + ".meta.json")
            pt_file.unlink()
            if meta_file.exists():
                meta_file.unlink()
            removed.append(pt_file.name)
    if removed:
        print(f"  [cleanup] 삭제된 개인화 모델: {removed}")
    return removed


# ─────────────────────────────────────────────────────────────────────
# Dataset
# ─────────────────────────────────────────────────────────────────────


class _PerUserMealDataset(Dataset):
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


# ─────────────────────────────────────────────────────────────────────
# 평가 헬퍼
# ─────────────────────────────────────────────────────────────────────


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


# ─────────────────────────────────────────────────────────────────────
# 핵심 학습 + 자동 폐기 (두 진입점이 공유)
# ─────────────────────────────────────────────────────────────────────


def _train_eval_save_or_reject(
    df_user: pd.DataFrame,
    user_id: str,
    base_model_path: Path,
    save_path: Path,
    epochs: int = 15,
    lr: float = 1e-4,
    finetune_ratio: float = 0.7,
    batch_size: int = 16,
    device: str = "cuda",
    seed: int = 42,
) -> dict[str, Any]:
    """공통 학습 로직. 자동 폐기 포함.

    Returns:
        dict {status, n_samples, base_rmse_30min, personalized_rmse_30min,
              improvement_percent, save_path}
    """
    device_t = torch.device(device if torch.cuda.is_available() else "cpu")
    print(f"  device: {device_t}")

    if len(df_user) < 10:
        raise ValueError(
            f"환자 {user_id} 데이터 너무 적음 ({len(df_user)} 건). 최소 10개 필요."
        )
    print(f"  환자 {user_id}: 총 {len(df_user)} 건")

    scaler = load_scaler()
    bg_scaler = scaler["bg_target"]

    # 70/30 split
    rng = np.random.default_rng(seed)
    indices = rng.permutation(len(df_user))
    n_ft = int(len(df_user) * finetune_ratio)
    df_ft = df_user.iloc[indices[:n_ft]].reset_index(drop=True)
    df_val = df_user.iloc[indices[n_ft:]].reset_index(drop=True)
    print(f"  finetune={len(df_ft)}, val={len(df_val)}")

    ft_loader = DataLoader(_PerUserMealDataset(df_ft), batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(_PerUserMealDataset(df_val), batch_size=batch_size, shuffle=False)

    # base 모델 로드
    if not base_model_path.exists():
        raise RuntimeError(f"base 모델 없음: {base_model_path}")
    model, _ = load_torch_model(base_model_path, MealLSTMDecoder)
    model = model.to(device_t)

    # base 평가
    base_rmse = _evaluate(model, val_loader, device_t, bg_scaler)
    base_rmse_30 = float(base_rmse[LABEL_STEPS.index(30)])
    _print_horizon_summary("base", base_rmse)

    # 임베딩 freeze
    for emb in model.cat_embed.embeddings:
        for p in emb.parameters():
            p.requires_grad = False

    # fine-tune
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
            best_rmse = float(rmse_30)
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    if best_state is not None:
        model.load_state_dict(best_state)

    # 결과 비교
    final_rmse = _evaluate(model, val_loader, device_t, bg_scaler)
    final_rmse_30 = float(final_rmse[LABEL_STEPS.index(30)])
    delta_30 = base_rmse_30 - final_rmse_30
    pct = 100 * delta_30 / base_rmse_30 if base_rmse_30 > 0 else 0.0

    print()
    _print_horizon_summary("base", base_rmse)
    _print_horizon_summary("pers", final_rmse)
    sign = "↓" if delta_30 > 0 else "↑"
    print(f"  Δ@30min = {abs(delta_30):.2f} mg/dL {sign}  ({pct:+.1f}%)")

    # 자동 폐기: personalized 가 base 보다 안 좋으면 저장 X
    if delta_30 <= 0:
        print(
            f"\n  ✗ rejected: base 보다 RMSE 개선 없음. 모델 저장 안 함, base 그대로 사용."
        )
        # 기존 personalized 파일이 있으면 삭제 (이전 학습 결과 유지하지 않음)
        if save_path.exists():
            save_path.unlink()
            meta_path = save_path.with_suffix(save_path.suffix + ".meta.json")
            if meta_path.exists():
                meta_path.unlink()
        return {
            "status": "rejected",
            "n_samples": int(len(df_user)),
            "base_rmse_30min": base_rmse_30,
            "personalized_rmse_30min": final_rmse_30,
            "improvement_percent": float(pct),
            "save_path": None,
        }

    # 저장
    save_torch_model(
        model,
        save_path,
        meta={
            "model_class": "MealLSTMDecoder",
            "base_model": str(base_model_path),
            "target_user_id": user_id,
            "n_finetune": int(len(df_ft)),
            "n_val": int(len(df_val)),
            "epochs": epochs,
            "lr": lr,
            "base_val_rmse_per_horizon": base_rmse.tolist(),
            "personalized_val_rmse_per_horizon": final_rmse.tolist(),
            "improvement_30min_mg_dl": delta_30,
        },
    )
    print(f"\n  ✓ saved: {save_path}")
    return {
        "status": "personalized",
        "n_samples": int(len(df_user)),
        "base_rmse_30min": base_rmse_30,
        "personalized_rmse_30min": final_rmse_30,
        "improvement_percent": float(pct),
        "save_path": str(save_path),
    }


# ─────────────────────────────────────────────────────────────────────
# 진입점 1: CLI (CSV 에서 환자 필터)
# ─────────────────────────────────────────────────────────────────────


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
) -> dict[str, Any]:
    """CLI 용. 정규화된 train/val csv 에서 user_id 컬럼으로 필터."""
    df = pd.read_csv(csv_with_user_id)
    if "user_id" not in df.columns:
        raise RuntimeError(f"{csv_with_user_id} 에 user_id 컬럼 없음.")
    df_user = df[df["user_id"] == target_user_id].reset_index(drop=True)

    return _train_eval_save_or_reject(
        df_user=df_user,
        user_id=target_user_id,
        base_model_path=base_model_path,
        save_path=save_path,
        epochs=epochs,
        lr=lr,
        finetune_ratio=finetune_ratio,
        batch_size=batch_size,
        device=device,
        seed=seed,
    )


# ─────────────────────────────────────────────────────────────────────
# 진입점 2: API (raw history dict 받아서 처리)
# ─────────────────────────────────────────────────────────────────────


def finetune_from_history(
    user_id: str,
    history: list[dict[str, Any]],
    user_profile: dict[str, Any],
    base_model_path: Path,
    save_path: Path,
    epochs: int = 15,
    lr: float = 1e-4,
    finetune_ratio: float = 0.7,
    batch_size: int = 16,
    device: str = "cuda",
    seed: int = 42,
) -> dict[str, Any]:
    """API 용. raw history list 를 정규화된 DataFrame 으로 변환 후 학습.

    Args:
        user_id: 환자 ID
        history: list of {carbs, meal_time_iso, current_glucose, bg_curve: [24]}
        user_profile: {fasting_bg, weight_kg, activity, diabetes_type, meal_pattern}
                      (모든 식사에 동일 적용)
        base_model_path: lstm_meal.pt
        save_path: 출력 경로 (lstm_meal_personalized_{user_id}.pt)
    """
    set_seed(seed)
    scaler = load_scaler()
    feature_scaler = scaler["model1_features"]
    bg_scaler = scaler["bg_target"]

    try:
        activity_int = ACTIVITY_MAP[user_profile["activity"]]
        dtype_int = DIABETES_TYPE_MAP[user_profile["diabetes_type"]]
        meal_pattern_int = MEAL_PATTERN_MAP[user_profile["meal_pattern"]]
    except KeyError as e:
        raise ValueError(f"unknown enum value: {e}") from e

    fasting_bg = float(user_profile["fasting_bg"])
    weight_kg = float(user_profile["weight_kg"])

    rows: list[dict[str, Any]] = []
    for item in history:
        carbs = float(item["carbs"])
        current_glucose = float(item["current_glucose"])
        bg_curve = list(item["bg_curve"])
        if len(bg_curve) != 24:
            raise ValueError(f"bg_curve must have 24 values, got {len(bg_curve)}")

        # meal_time → sin/cos
        dt = datetime.fromisoformat(item["meal_time_iso"].replace("Z", "+00:00"))
        hour = dt.hour + dt.minute / 60.0 + dt.second / 3600.0
        sin_t = math.sin(2 * math.pi * hour / 24.0)
        cos_t = math.cos(2 * math.pi * hour / 24.0)

        # 4개 컬럼 정규화: [carbs, current_glucose, fasting_bg, weight_kg]
        raw_4 = np.array([[carbs, current_glucose, fasting_bg, weight_kg]], dtype=np.float32)
        scaled_4 = feature_scaler.transform(raw_4)[0]

        # BG 정규화
        bg_norm = bg_scaler.transform(np.array(bg_curve).reshape(-1, 1)).flatten()

        row: dict[str, Any] = {
            "user_id": user_id,
            "carbs": float(scaled_4[0]),
            "meal_time_sin": sin_t,
            "meal_time_cos": cos_t,
            "current_glucose": float(scaled_4[1]),
            "fasting_bg": float(scaled_4[2]),
            "weight_kg": float(scaled_4[3]),
            "activity": activity_int,
            "diabetes_type": dtype_int,
            "meal_pattern": meal_pattern_int,
        }
        for i, t in enumerate(LABEL_STEPS):
            row[f"BG_{t}min"] = float(bg_norm[i])
        rows.append(row)

    df_user = pd.DataFrame(rows)
    return _train_eval_save_or_reject(
        df_user=df_user,
        user_id=user_id,
        base_model_path=base_model_path,
        save_path=save_path,
        epochs=epochs,
        lr=lr,
        finetune_ratio=finetune_ratio,
        batch_size=batch_size,
        device=device,
        seed=seed,
    )


# ─────────────────────────────────────────────────────────────────────
# CLI
# ─────────────────────────────────────────────────────────────────────


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
    result = finetune_meal(
        base_model_path=Path(args.base),
        csv_with_user_id=Path(args.csv),
        target_user_id=args.user,
        save_path=save_path,
        epochs=args.epochs,
        lr=args.lr,
        batch_size=args.batch_size,
        device=args.device,
    )
    print(f"\nresult: {result}")


if __name__ == "__main__":
    main()
