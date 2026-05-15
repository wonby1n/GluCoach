"""
Stage2 공정 비교: shanghai_stage2.csv 동일 데이터로 ShanghaiLSTM 학습 후
XGBoost(Stage2)와 성능 비교.

사용법:
  cd ai
  python scripts/train_stage2_lstm.py

동일 조건:
  - 데이터: data/processed/shanghai_stage2.csv
  - Feature 11개 (Stage2 meta.json 기준):
      carbs_g, protein_g, fat_g, fiber_g, kcal, pre_meal_glucose,
      hour_sin, hour_cos, diabetes_type, carb_ratio, protein_fat
  - Split: user 기준 70/15/15, seed=42 (Stage2 train_stage2.py 와 동일)

출력:
  - models/lstm_meal_shanghai.pt
  - models/lstm_shanghai_scaler.pkl
  - eval/stage2_compare/comparison_table.png
  - eval/stage2_compare/lstm_curves.png
  - eval/stage2_compare/stage2_curves.png
  - eval/stage2_compare/results.json
"""
from __future__ import annotations

import json
import pickle
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, TensorDataset

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))

from app.glucose.curve_builder import build_curve
from app.glucose.stage2_model import (
    _glycemic_scale_factor,
    _linear_peak_prior,
    _linear_ttp_prior,
    get_stage2_predictor,
)
from evaluate import (
    ceg_per_horizon,
    generate_comparison_table,
    mae_per_horizon,
    peak_errors,
    plot_curve_examples,
    rmse_per_horizon,
)

# ── 상수 ─────────────────────────────────────────────────────────────────────

DATA_PATH = ROOT / "data" / "processed" / "shanghai_stage2.csv"
MODEL_PATH = ROOT / "models" / "lstm_meal_shanghai.pt"
SCALER_PATH = ROOT / "models" / "lstm_shanghai_scaler.pkl"
OUT_DIR = ROOT / "eval" / "stage2_compare"

RANDOM_SEED = 42
BG_COLS = [f"BG_{t}min" for t in range(5, 125, 5)]
LABEL_STEPS = list(range(5, 125, 5))
KEY_HORIZONS = [30, 60, 120]

# Stage2 train_stage2.py 와 동일한 매핑
DTYPE_MAP_TRAIN = {"T1DM": 1, "T2DM": 2, "Normal": 0}
# stage2_model.py _DTYPE_MAP 은 T1D/T2D 키를 사용하므로 변환 필요
DTYPE_MAP_PREDICTOR = {"T1DM": "T1D", "T2DM": "T2D", "Normal": "Normal"}

PRE_GLUCOSE_MIN, PRE_GLUCOSE_MAX = 50.0, 400.0
MIN_CLEAN_BG_POINTS = 20

# Stage2 meta.json 과 동일한 feature 순서
FEATURE_NAMES = [
    "carbs_g", "protein_g", "fat_g", "fiber_g", "kcal",
    "pre_meal_glucose", "hour_sin", "hour_cos",
    "diabetes_type", "carb_ratio", "protein_fat",
]
N_FEATURES = len(FEATURE_NAMES)  # 11


# ── 데이터 전처리 ─────────────────────────────────────────────────────────────

def filter_valid(df: pd.DataFrame) -> pd.DataFrame:
    df = df[
        (df["pre_meal_glucose"] >= PRE_GLUCOSE_MIN) &
        (df["pre_meal_glucose"] <= PRE_GLUCOSE_MAX)
    ].copy()
    valid_mask = (~df[BG_COLS].isna()).sum(axis=1) >= MIN_CLEAN_BG_POINTS
    df = df[valid_mask].copy()
    for col in BG_COLS:
        df[col] = df[col].interpolate(method="linear", limit_direction="both")
    return df.reset_index(drop=True)


def build_features(df: pd.DataFrame) -> np.ndarray:
    """Stage2 와 동일한 11개 feature 추출. feature 순서는 FEATURE_NAMES 와 일치."""
    meal_time = pd.to_datetime(df["meal_time"], errors="coerce")
    hour = meal_time.dt.hour.fillna(12).astype(float)

    carbs   = df["carbs_g"].values.astype(float)
    protein = df["protein_g"].values.astype(float)
    fat     = df["fat_g"].values.astype(float)
    fiber   = df["fiber_g"].values.astype(float)
    kcal    = df["kcal"].values.astype(float)
    pre_gl  = df["pre_meal_glucose"].values.astype(float)
    hour_sin = np.sin(2 * np.pi * hour / 24)
    hour_cos = np.cos(2 * np.pi * hour / 24)
    dtype    = df["diabetes_type"].map(DTYPE_MAP_TRAIN).fillna(0).values.astype(float)
    total    = carbs + protein + fat + 1e-6
    carb_ratio  = carbs / total
    protein_fat = protein + fat

    return np.stack(
        [carbs, protein, fat, fiber, kcal, pre_gl,
         hour_sin, hour_cos, dtype, carb_ratio, protein_fat],
        axis=1,
    ).astype(np.float32)


def build_targets(df: pd.DataFrame) -> np.ndarray:
    """BG delta = BG_t - pre_meal_glucose. [N, 24]"""
    pre = df["pre_meal_glucose"].values.astype(np.float32).reshape(-1, 1)
    bg  = df[BG_COLS].values.astype(np.float32)
    return bg - pre


def compute_prior_delta_row(
    carbs: float, protein: float, fat: float, fiber: float, pre_gl: float,
    carb_coef: float = 0.80,
) -> np.ndarray:
    """단일 샘플에 대해 linear prior delta [24] 계산.

    carb_coef: 탄수화물 계수. 기본 0.80 (Zewei 2015, 메트포르민 T2DM 기준).
               비약물 일반인 대상 시 1.2~2.0 범위로 상향 권장.
    """
    lin_peak = max(0.0, 10.0 + carbs * carb_coef
                   - fiber * 1.50 - fat * 0.30 - protein * 0.20)
    lin_ttp   = _linear_ttp_prior(carbs, fat, fiber, protein)
    scale     = _glycemic_scale_factor(carbs, protein)
    lin_peak  = lin_peak * scale
    curve_abs = build_curve(lin_peak, lin_ttp, 3.0, pre_gl)
    return np.array(curve_abs, dtype=np.float32) - pre_gl


def build_prior_deltas(df: pd.DataFrame, carb_coef: float = 0.80) -> np.ndarray:
    """전체 DataFrame에 대해 vectorized prior delta [N, 24] 계산.

    carb_coef: 탄수화물 계수. 기본 0.80 (메트포르민 T2DM 기준).
    """
    carbs   = df["carbs_g"].values.astype(float)
    protein = df["protein_g"].values.astype(float)
    fat     = df["fat_g"].values.astype(float)
    fiber   = df["fiber_g"].values.astype(float)
    pre_gl  = df["pre_meal_glucose"].values.astype(float)

    lin_peak = np.maximum(
        0.0,
        10.0 + carbs * carb_coef - fiber * 1.50 - fat * 0.30 - protein * 0.20,
    )
    lin_ttp = np.clip(
        35.0 + fat * 0.50 + fiber * 0.30 + protein * 0.20 - carbs * 0.10,
        10.0, 110.0,
    )
    effective = np.maximum(0.0, carbs + protein * 0.08)
    scale     = (effective ** 2) / (effective ** 2 + 25.0)
    lin_peak  = lin_peak * scale

    priors = np.zeros((len(df), 24), dtype=np.float32)
    for i in range(len(df)):
        curve_abs = build_curve(float(lin_peak[i]), float(lin_ttp[i]), 3.0, float(pre_gl[i]))
        priors[i] = np.array(curve_abs, dtype=np.float32) - pre_gl[i]
    return priors


def delta_to_absolute(y_delta: np.ndarray, pre_glucose: np.ndarray) -> np.ndarray:
    """delta [N, 24] + pre_glucose [N] → absolute BG [N, 24]."""
    return y_delta + pre_glucose.reshape(-1, 1)


def user_split(df: pd.DataFrame, val_ratio: float = 0.15, test_ratio: float = 0.15):
    """Stage2 train_stage2.py 와 동일한 user 기준 split."""
    np.random.seed(RANDOM_SEED)
    users = df["user_id"].unique()
    np.random.shuffle(users)
    n = len(users)
    n_test = max(1, int(n * test_ratio))
    n_val  = max(1, int(n * val_ratio))
    test_users  = set(users[:n_test])
    val_users   = set(users[n_test:n_test + n_val])
    train_users = set(users[n_test + n_val:])
    return (
        df[df["user_id"].isin(train_users)].copy(),
        df[df["user_id"].isin(val_users)].copy(),
        df[df["user_id"].isin(test_users)].copy(),
    )


# ── LSTM 모델 ─────────────────────────────────────────────────────────────────

class ShanghaiLSTM(nn.Module):
    """Stage2 동일 feature → 24-step BG 직접 예측.

    diabetes_type 은 Embedding으로 처리, 나머지 연속 feature 는 Linear encoder.
    dtype_idx: feature 벡터에서 diabetes_type 의 위치 (기본 8, pre_meal_glucose 제거 시 7).
    """

    def __init__(
        self,
        n_features: int = N_FEATURES,
        hidden: int = 64,
        n_steps: int = 24,
        step_emb_dim: int = 16,
        dtype_emb_dim: int = 8,
        dropout: float = 0.2,
        dtype_idx: int = 8,
    ) -> None:
        super().__init__()
        self.dtype_idx = dtype_idx
        n_cont = n_features - 1  # diabetes_type 제외한 연속 feature 수
        self.dtype_embed = nn.Embedding(3, dtype_emb_dim)  # T1DM/T2DM/Normal
        self.encoder = nn.Sequential(
            nn.Linear(n_cont + dtype_emb_dim, hidden),
            nn.ReLU(),
            nn.Dropout(dropout),
        )
        self.step_embed = nn.Embedding(n_steps, step_emb_dim)
        self.cell = nn.LSTMCell(hidden + step_emb_dim, hidden)
        self.head = nn.Sequential(
            nn.Dropout(dropout),
            nn.Linear(hidden, 1),
        )
        self.n_steps = n_steps

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # diabetes_type 분리
        dtype_idx = x[:, self.dtype_idx].long().clamp(0, 2)
        cont = torch.cat([x[:, :self.dtype_idx], x[:, self.dtype_idx + 1:]], dim=1)
        dtype_e = self.dtype_embed(dtype_idx)          # [B, dtype_emb_dim]
        ctx = self.encoder(torch.cat([cont, dtype_e], dim=1))  # [B, hidden]
        h, c = ctx, torch.zeros_like(ctx)
        step_embeds = self.step_embed(
            torch.arange(self.n_steps, device=x.device)
        )                              # [n_steps, step_emb_dim]
        outputs = []
        for i in range(self.n_steps):
            step_e = step_embeds[i].unsqueeze(0).expand(ctx.size(0), -1)
            inp = torch.cat([ctx, step_e], dim=1)
            h, c = self.cell(inp, (h, c))
            outputs.append(self.head(h))
        return torch.cat(outputs, dim=1)  # [B, 24]


# ── 학습 ─────────────────────────────────────────────────────────────────────

def train_lstm(
    X_train: np.ndarray,
    y_train: np.ndarray,
    X_val: np.ndarray,
    y_val: np.ndarray,
    epochs: int = 300,
    batch_size: int = 32,
    lr: float = 1e-3,
    patience: int = 30,
    n_features: int = N_FEATURES,
    dtype_idx: int = 8,
) -> ShanghaiLSTM:
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"  device: {device}")

    Xt = torch.from_numpy(X_train)
    yt = torch.from_numpy(y_train)
    Xv = torch.from_numpy(X_val).to(device)
    yv = torch.from_numpy(y_val).to(device)

    loader = DataLoader(TensorDataset(Xt, yt), batch_size=batch_size, shuffle=True)

    model = ShanghaiLSTM(n_features=n_features, dtype_idx=dtype_idx).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=1e-5)
    # peak 구간(30~90min = index 5~17)에 3배 가중치
    _w = torch.ones(24, device=device)
    _w[5:18] = 3.0
    loss_fn = lambda pred, tgt: (_w * (pred - tgt).pow(2)).mean()

    best_val = float("inf")
    best_state = None
    no_improve = 0

    for epoch in range(1, epochs + 1):
        model.train()
        train_loss = 0.0
        for xb, yb in loader:
            xb, yb = xb.to(device), yb.to(device)
            loss = loss_fn(model(xb), yb)
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            train_loss += loss.item() * xb.size(0)
        train_loss /= len(X_train)
        scheduler.step()

        model.eval()
        with torch.no_grad():
            val_loss = loss_fn(model(Xv), yv).item()

        if epoch % 50 == 0 or epoch == 1:
            current_lr = scheduler.get_last_lr()[0]
            print(f"  epoch {epoch:>3}/{epochs}  train={train_loss:.4f}  val={val_loss:.4f}  lr={current_lr:.2e}")

        if val_loss < best_val:
            best_val = val_loss
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
            no_improve = 0
        else:
            no_improve += 1
            if no_improve >= patience:
                print(f"  조기 종료 (epoch {epoch}, best_val={best_val:.4f})")
                break

    model.load_state_dict(best_state)
    return model


# ── 예측 ─────────────────────────────────────────────────────────────────────

def predict_lstm(
    model: ShanghaiLSTM,
    X: np.ndarray,
    bg_scaler: StandardScaler,
    prior_deltas: np.ndarray | None = None,
    batch_size: int = 256,
) -> np.ndarray:
    """정규화된 X → delta 예측값 [N, 24].

    prior_deltas 가 주어지면 residual + prior → total delta 로 복원.
    """
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.eval()
    preds = []
    loader = DataLoader(TensorDataset(torch.from_numpy(X)), batch_size=batch_size)
    with torch.no_grad():
        for (xb,) in loader:
            preds.append(model(xb.to(device)).cpu().numpy())
    y_norm     = np.concatenate(preds, axis=0)
    y_residual = bg_scaler.inverse_transform(y_norm.reshape(-1, 1)).reshape(-1, 24)
    if prior_deltas is not None:
        return (y_residual + prior_deltas).astype(np.float32)
    return y_residual.astype(np.float32)


def predict_stage2(df_test: pd.DataFrame) -> np.ndarray:
    """Stage2(XGBoost + linear prior + curve_builder) → [N, 24] raw mg/dL."""
    predictor = get_stage2_predictor()
    meal_times = pd.to_datetime(df_test["meal_time"], errors="coerce")
    preds = []
    for idx, row in df_test.iterrows():
        mt = meal_times[idx]
        hour = mt.hour if pd.notna(mt) else 12
        dtype_str = DTYPE_MAP_PREDICTOR.get(str(row["diabetes_type"]), "T2D")
        inp = {
            "carbs_g":          float(row["carbs_g"]),
            "protein_g":        float(row["protein_g"]),
            "fat_g":            float(row["fat_g"]),
            "fiber_g":          float(row["fiber_g"]),
            "kcal":             float(row["kcal"]),
            "pre_meal_glucose": float(row["pre_meal_glucose"]),
            "meal_hour":        float(hour),
            "diabetes_type":    dtype_str,
        }
        scalars = predictor.predict(inp)
        curve = build_curve(
            peak_delta=scalars["peak_delta"],
            time_to_peak=scalars["time_to_peak"],
            decay_rate=scalars["decay_rate"],
            baseline_glucose=float(row["pre_meal_glucose"]),
        )
        preds.append(curve)
    return np.array(preds, dtype=np.float32)


# ── 메트릭 출력 ───────────────────────────────────────────────────────────────

def print_metrics(label: str, y_true: np.ndarray, y_pred: np.ndarray) -> dict:
    rmse = rmse_per_horizon(y_true, y_pred)
    mae  = mae_per_horizon(y_true, y_pred)
    ceg  = ceg_per_horizon(y_true, y_pred)
    pv, pt = peak_errors(y_true, y_pred)

    print(f"\n[{label}]")
    print(f"  {'':>10}  {'RMSE':>8}  {'MAE':>8}  {'CEG A+B':>9}")
    for h in KEY_HORIZONS:
        i = LABEL_STEPS.index(h)
        print(f"  {h}min      {rmse[i]:>8.2f}  {mae[i]:>8.2f}  {ceg[i]*100:>8.1f}%")
    print(f"  Peak val err: {pv:.2f} mg/dL  Peak time err: {pt:.1f} min")

    return {
        "rmse_per_horizon":        rmse.tolist(),
        "mae_per_horizon":         mae.tolist(),
        "ceg_a_plus_b_per_horizon": ceg.tolist(),
        "peak_value_error_mg_dl":  pv,
        "peak_time_error_min":     pt,
        "n_samples":               int(y_true.shape[0]),
    }


# ── 메인 ─────────────────────────────────────────────────────────────────────

def main() -> None:
    print("=" * 60)
    print("Stage2 공정 비교: ShanghaiLSTM vs XGBoost(Stage2)")
    print("=" * 60)

    # ── 1. 데이터 로드 및 전처리 ──────────────────────────────────────────────
    print(f"\n[1] 데이터 로드: {DATA_PATH}")
    df = pd.read_csv(DATA_PATH)
    print(f"  원본: {len(df)}행")
    df = filter_valid(df)
    print(f"  유효: {len(df)}행")

    X_all     = build_features(df)
    delta_all = build_targets(df)   # actual delta [N, 24]

    # ── 2. Split (Stage2 동일) ────────────────────────────────────────────────
    print("\n[2] User 기준 split (seed=42, 70/15/15):")
    df_train, df_val, df_test = user_split(df)
    print(f"  train={len(df_train)}, val={len(df_val)}, test={len(df_test)}")

    idx_train = df_train.index
    idx_val   = df_val.index
    idx_test  = df_test.index

    X_train = X_all[idx_train]
    X_val   = X_all[idx_val]
    X_test  = X_all[idx_test]
    y_test  = delta_all[idx_test]   # actual delta (평가용)

    # ── 3. Linear prior 계산 ─────────────────────────────────────────────────
    print("\n[3] Linear prior delta 계산 (Stage2 동일 계수):")
    prior_all   = build_prior_deltas(df)
    prior_train = prior_all[idx_train]
    prior_val   = prior_all[idx_val]
    prior_test  = prior_all[idx_test]
    print(f"  prior peak 평균: {prior_train.max(axis=1).mean():.2f} mg/dL delta")

    # residual = actual_delta - prior_delta (LSTM 이 학습할 대상)
    y_train_res = delta_all[idx_train] - prior_train
    y_val_res   = delta_all[idx_val]   - prior_val
    print(f"  residual mean: {y_train_res.mean():.2f}, std: {y_train_res.std():.2f}")

    # ── 4. 정규화 ─────────────────────────────────────────────────────────────
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

    # ── 5. LSTM 학습 ──────────────────────────────────────────────────────────
    print("\n[5] ShanghaiLSTM 학습 (linear prior residual):")
    model = train_lstm(X_train_s, y_train_s, X_val_s, y_val_s)

    # ── 6. 저장 ───────────────────────────────────────────────────────────────
    print("\n[6] 모델 & 스케일러 저장:")
    torch.save(model.state_dict(), MODEL_PATH)
    with open(SCALER_PATH, "wb") as f:
        pickle.dump({"feature": feat_scaler, "bg_target": bg_scaler}, f)
    print(f"  {MODEL_PATH}")
    print(f"  {SCALER_PATH}")

    # ── 7. 예측 ───────────────────────────────────────────────────────────────
    print("\n[7] Test set 예측:")
    print("  LSTM 추론 중...")
    y_pred_lstm_delta = predict_lstm(model, X_test_s, bg_scaler, prior_deltas=prior_test)

    print("  Stage2(XGBoost) 추론 중...")
    y_pred_stage2 = predict_stage2(df_test.reset_index(drop=True))

    # delta → absolute (평가는 절대값 기준)
    pre_glucose_test = df_test["pre_meal_glucose"].values.astype(np.float32)
    y_pred_lstm  = delta_to_absolute(y_pred_lstm_delta, pre_glucose_test)
    y_test_abs   = delta_to_absolute(y_test, pre_glucose_test)

    # ── 8. 평가 & 비교 ────────────────────────────────────────────────────────
    print("\n[8] 성능 비교 (test set, raw mg/dL 기준):")
    metrics_lstm   = print_metrics("ShanghaiLSTM+Prior",  y_test_abs, y_pred_lstm)
    metrics_stage2 = print_metrics("Stage2(XGBoost)",     y_test_abs, y_pred_stage2)

    metrics_lstm["model_name"]   = "ShanghaiLSTM+LinearPrior"
    metrics_lstm["model_type"]   = "lstm"
    metrics_stage2["model_name"] = "Stage2(XGBoost+LinearPrior)"
    metrics_stage2["model_type"] = "xgboost"

    # ── 9. 결과 저장 ──────────────────────────────────────────────────────────
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    results_path = OUT_DIR / "results.json"
    with open(results_path, "w", encoding="utf-8") as f:
        json.dump(
            {"lstm": metrics_lstm, "stage2": metrics_stage2},
            f, indent=2, ensure_ascii=False,
        )
    print(f"\n[9] 결과 저장: {results_path}")

    generate_comparison_table(
        [metrics_lstm, metrics_stage2],
        save_path=OUT_DIR / "comparison_table.png",
    )
    plot_curve_examples(
        y_test_abs, y_pred_lstm,
        save_path=OUT_DIR / "lstm_curves.png",
        n_examples=5,
        title_prefix="ShanghaiLSTM+Prior: ",
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
