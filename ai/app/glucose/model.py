"""혈당 예측 모델 4종.

Model 1 (식사 시점): MealRidge, MealMLP, MealLSTMDecoder
Model 2 (현재 시점): NowLSTM

상세 사양: CLAUDE.md, DATA_SPEC.md
"""

from __future__ import annotations

import hashlib
import json
import pickle
from pathlib import Path
from typing import Any

import numpy as np
import torch
import torch.nn as nn
from sklearn.linear_model import Ridge
from sklearn.multioutput import MultiOutputRegressor

from app.glucose.constants import DEFAULT_SCALER_PATH

from app.glucose.constants import (
    CATEGORICAL_CARDINALITIES,
    EMBEDDING_DIM,
    HIDDEN_DIM,
    OUTPUT_DIM,
    PROFILE_DIM,
    SEQ_LEN,
)


# ─────────────────────────────────────────────────────────────────────
# Model 1: 식사 시점 → 식후 120분 BG (24개)
# ─────────────────────────────────────────────────────────────────────


class MealRidge:
    """sklearn Ridge + MultiOutputRegressor 래퍼.

    카테고리 3개를 one-hot 으로 펼쳐서 고정 차원 입력.
    입력 차원: 6 (continuous) + 3 + 3 + 11 (one-hot) = 23
    """

    INPUT_DIM = 6 + sum(CATEGORICAL_CARDINALITIES)  # 23

    def __init__(self, alpha: float = 1.0) -> None:
        self.alpha = alpha
        self.model: MultiOutputRegressor | None = None

    @staticmethod
    def encode_categorical(x_cat: np.ndarray) -> np.ndarray:
        """[N, 3] 정수 → [N, 17] one-hot."""
        n = x_cat.shape[0]
        parts = []
        for i, card in enumerate(CATEGORICAL_CARDINALITIES):
            oh = np.zeros((n, card), dtype=np.float32)
            oh[np.arange(n), x_cat[:, i].astype(int)] = 1.0
            parts.append(oh)
        return np.concatenate(parts, axis=1)

    @classmethod
    def build_input(cls, x_cont: np.ndarray, x_cat: np.ndarray) -> np.ndarray:
        return np.concatenate([x_cont, cls.encode_categorical(x_cat)], axis=1)

    def fit(self, x_cont: np.ndarray, x_cat: np.ndarray, y: np.ndarray) -> None:
        x = self.build_input(x_cont, x_cat)
        self.model = MultiOutputRegressor(Ridge(alpha=self.alpha))
        self.model.fit(x, y)

    def predict(self, x_cont: np.ndarray, x_cat: np.ndarray) -> np.ndarray:
        if self.model is None:
            raise RuntimeError("Model not fitted")
        return self.model.predict(self.build_input(x_cont, x_cat))

    def save(self, path: str | Path, scaler_path: str | Path = DEFAULT_SCALER_PATH) -> None:
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        scaler_stats = _extract_scaler_stats(scaler_path)
        with open(path, "wb") as f:
            pickle.dump(
                {
                    "alpha": self.alpha,
                    "model": self.model,
                    "scaler_hash": compute_scaler_hash(scaler_path),
                    "scaler_path": str(scaler_path),
                    "scaler_stats": scaler_stats,
                },
                f,
            )

    @classmethod
    def load(
        cls,
        path: str | Path,
        scaler_path: str | Path = DEFAULT_SCALER_PATH,
        strict_scaler: bool = True,
    ) -> "MealRidge":
        with open(path, "rb") as f:
            obj = pickle.load(f)
        m = cls(alpha=obj["alpha"])
        m.model = obj["model"]
        expected_hash = obj.get("scaler_hash")
        if expected_hash:
            actual_hash = compute_scaler_hash(scaler_path)
            if actual_hash != expected_hash:
                msg = (
                    f"scaler 일관성 위반 (MealRidge): 학습 시 {expected_hash[:12]} "
                    f"vs 현재 {actual_hash[:12] if actual_hash else 'None'}. 재학습 필요."
                )
                if strict_scaler:
                    raise RuntimeError(msg)
                else:
                    print(f"  [WARNING] {msg}")
        return m


class _CategoricalEmbedding(nn.Module):
    """3개 카테고리 임베딩 후 concat."""

    def __init__(self, embedding_dim: int = EMBEDDING_DIM) -> None:
        super().__init__()
        self.embeddings = nn.ModuleList(
            [nn.Embedding(card, embedding_dim) for card in CATEGORICAL_CARDINALITIES]
        )
        self.out_dim = embedding_dim * len(CATEGORICAL_CARDINALITIES)

    def forward(self, x_cat: torch.Tensor) -> torch.Tensor:  # [B, 3] long
        embedded = [emb(x_cat[:, i]) for i, emb in enumerate(self.embeddings)]
        return torch.cat(embedded, dim=1)  # [B, embedding_dim * 3]


class MealMLP(nn.Module):
    """식사 시점 → 24-step BG. 단순 MLP."""

    def __init__(
        self,
        n_continuous: int = 6,
        embedding_dim: int = EMBEDDING_DIM,
        hidden: int = HIDDEN_DIM,
        dropout: float = 0.2,
        output_dim: int = OUTPUT_DIM,
    ) -> None:
        super().__init__()
        self.cat_embed = _CategoricalEmbedding(embedding_dim)
        in_dim = n_continuous + self.cat_embed.out_dim
        self.net = nn.Sequential(
            nn.Linear(in_dim, hidden),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden, hidden),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden, output_dim),
        )

    def forward(self, x_cont: torch.Tensor, x_cat: torch.Tensor) -> torch.Tensor:
        x = torch.cat([x_cont, self.cat_embed(x_cat)], dim=1)
        return self.net(x)


class MealLSTMDecoder(nn.Module):
    """식사 시점 → encoder → LSTMCell 24-step decoding (step embedding 포함).

    개선:
    - step embedding: 매 디코딩 step 의 시간 정보(0~23)를 학습 가능한 vector 로
      cell 입력에 concat → 모델이 "지금 몇 분째 예측" 명시적으로 인지
    - h0/c0 학습 가능 init parameter
    - head 에 dropout 추가
    """

    def __init__(
        self,
        n_continuous: int = 6,
        embedding_dim: int = EMBEDDING_DIM,
        hidden: int = HIDDEN_DIM,
        n_steps: int = OUTPUT_DIM,
        step_emb_dim: int = 16,
        dropout: float = 0.2,
    ) -> None:
        super().__init__()
        self.cat_embed = _CategoricalEmbedding(embedding_dim)
        in_dim = n_continuous + self.cat_embed.out_dim
        self.encoder = nn.Sequential(
            nn.Linear(in_dim, hidden),
            nn.ReLU(),
            nn.Dropout(dropout),
        )
        # 각 디코딩 step (0~n_steps-1) 의 시간 정보 학습 가능 embedding
        self.step_embed = nn.Embedding(n_steps, step_emb_dim)
        self.cell = nn.LSTMCell(hidden + step_emb_dim, hidden)
        self.head = nn.Sequential(
            nn.Dropout(dropout),
            nn.Linear(hidden, 1),
        )
        self.n_steps = n_steps
        self.hidden_dim = hidden

    def forward(self, x_cont: torch.Tensor, x_cat: torch.Tensor) -> torch.Tensor:
        x = torch.cat([x_cont, self.cat_embed(x_cat)], dim=1)
        ctx = self.encoder(x)  # [B, hidden]
        batch = ctx.size(0)
        h = ctx
        c = torch.zeros_like(ctx)
        # step indices: [0, 1, ..., n_steps-1] → embed → [n_steps, step_emb_dim]
        step_indices = torch.arange(self.n_steps, device=ctx.device)
        step_embeds = self.step_embed(step_indices)  # [n_steps, step_emb_dim]

        outputs = []
        for i in range(self.n_steps):
            step_e = step_embeds[i].unsqueeze(0).expand(batch, -1)  # [B, step_emb_dim]
            inp = torch.cat([ctx, step_e], dim=1)  # [B, hidden + step_emb_dim]
            h, c = self.cell(inp, (h, c))
            outputs.append(self.head(h))  # [B, 1]
        return torch.cat(outputs, dim=1)  # [B, n_steps]


# ─────────────────────────────────────────────────────────────────────
# Model 2: 현재 시점 → 향후 120분 BG (24개)
# ─────────────────────────────────────────────────────────────────────


class NowLSTM(nn.Module):
    """시계열 BG + profile → 24-step forecasting.

    profile 4-dim 을 모든 timestep 에 concat 하여 [B, 12, 5] 로 LSTM 입력.
    """

    def __init__(
        self,
        seq_len: int = SEQ_LEN,
        profile_dim: int = PROFILE_DIM,
        hidden: int = HIDDEN_DIM,
        n_layers: int = 2,
        dropout: float = 0.2,
        output_dim: int = OUTPUT_DIM,
    ) -> None:
        super().__init__()
        self.seq_len = seq_len
        self.profile_dim = profile_dim
        self.lstm = nn.LSTM(
            input_size=1 + profile_dim,
            hidden_size=hidden,
            num_layers=n_layers,
            batch_first=True,
            dropout=dropout if n_layers > 1 else 0.0,
        )
        self.head = nn.Linear(hidden, output_dim)

    def forward(self, x_seq: torch.Tensor, x_profile: torch.Tensor) -> torch.Tensor:
        # x_seq: [B, 12], x_profile: [B, 4]
        x_seq_3d = x_seq.unsqueeze(-1)  # [B, 12, 1]
        prof_3d = x_profile.unsqueeze(1).expand(-1, self.seq_len, -1)  # [B, 12, 4]
        x = torch.cat([x_seq_3d, prof_3d], dim=2)  # [B, 12, 5]
        _, (h, _) = self.lstm(x)
        last = h[-1]  # [B, hidden]
        return self.head(last)  # [B, 24]


# ─────────────────────────────────────────────────────────────────────
# 저장/로드 헬퍼 (torch 모델용)
# ─────────────────────────────────────────────────────────────────────


def compute_scaler_hash(scaler_path: str | Path = DEFAULT_SCALER_PATH) -> str | None:
    """scaler.pkl 의 sha256 hash. 없으면 None."""
    path = Path(scaler_path)
    if not path.exists():
        return None
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _extract_scaler_stats(scaler_path: str | Path) -> dict[str, Any] | None:
    """scaler.pkl 에서 사람이 읽을 수 있는 통계 추출."""
    path = Path(scaler_path)
    if not path.exists():
        return None
    try:
        with open(path, "rb") as f:
            scaler = pickle.load(f)
        return {
            "bg_mean": float(scaler["bg_target"].mean_[0]),
            "bg_std": float(scaler["bg_target"].scale_[0]),
        }
    except Exception:
        return None


def save_torch_model(
    model: nn.Module,
    path: str | Path,
    meta: dict[str, Any],
    scaler_path: str | Path = DEFAULT_SCALER_PATH,
) -> None:
    """모델 + meta 저장. meta 에 scaler hash 및 통계 자동 기록."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save(model.state_dict(), path)
    meta = dict(meta)
    meta["scaler_hash"] = compute_scaler_hash(scaler_path)
    meta["scaler_path"] = str(scaler_path)
    scaler_stats = _extract_scaler_stats(scaler_path)
    if scaler_stats:
        meta["scaler_stats"] = scaler_stats
    with open(path.with_suffix(path.suffix + ".meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)


def load_torch_model(
    path: str | Path,
    model_class: type[nn.Module],
    scaler_path: str | Path = DEFAULT_SCALER_PATH,
    strict_scaler: bool = True,
    **init_kwargs: Any,
) -> tuple[nn.Module, dict[str, Any]]:
    """모델 + meta 로드. scaler hash 검증.

    strict_scaler=True (default): scaler 가 학습 시점과 다르면 RuntimeError.
    학습 외 dev 환경에선 strict_scaler=False 로 우회 가능.
    """
    path = Path(path)
    model = model_class(**init_kwargs)
    model.load_state_dict(torch.load(path, map_location="cpu", weights_only=True))
    model.eval()
    meta_path = path.with_suffix(path.suffix + ".meta.json")
    meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}

    expected_hash = meta.get("scaler_hash")
    if expected_hash:
        actual_hash = compute_scaler_hash(scaler_path)
        if actual_hash != expected_hash:
            msg = (
                f"scaler 일관성 위반: 모델 학습 시 hash={expected_hash[:12]}, "
                f"현재 scaler hash={actual_hash[:12] if actual_hash else 'None'}. "
                f"preprocess 가 다시 실행되어 scaler 가 갱신된 것 같음. 모델 재학습 필요."
            )
            if strict_scaler:
                raise RuntimeError(msg)
            else:
                print(f"  [WARNING] {msg}")

    return model, meta


# ─────────────────────────────────────────────────────────────────────
# 더미 입력 forward 테스트
# ─────────────────────────────────────────────────────────────────────


def _smoke_test() -> None:
    print("=" * 60)
    print("모델 4종 smoke test")
    print("=" * 60)
    batch = 4
    x_cont = torch.randn(batch, 6)
    x_cat = torch.tensor(
        [[0, 0, 0], [1, 1, 5], [2, 2, 10], [0, 1, 7]],
        dtype=torch.long,
    )

    print("\n[Model 1] MealMLP")
    mlp = MealMLP()
    out = mlp(x_cont, x_cat)
    n_params = sum(p.numel() for p in mlp.parameters())
    print(f"  output shape: {tuple(out.shape)}  (expected [{batch}, {OUTPUT_DIM}])")
    print(f"  parameters: {n_params:,}")

    print("\n[Model 1] MealLSTMDecoder")
    lstm_dec = MealLSTMDecoder()
    out = lstm_dec(x_cont, x_cat)
    n_params = sum(p.numel() for p in lstm_dec.parameters())
    print(f"  output shape: {tuple(out.shape)}  (expected [{batch}, {OUTPUT_DIM}])")
    print(f"  parameters: {n_params:,}")

    print("\n[Model 1] MealRidge (sklearn)")
    ridge = MealRidge(alpha=1.0)
    y_dummy = np.random.randn(batch, OUTPUT_DIM).astype(np.float32)
    ridge.fit(x_cont.numpy(), x_cat.numpy(), y_dummy)
    out_np = ridge.predict(x_cont.numpy(), x_cat.numpy())
    print(f"  output shape: {out_np.shape}  (expected ({batch}, {OUTPUT_DIM}))")

    print("\n[Model 2] NowLSTM")
    x_seq = torch.randn(batch, SEQ_LEN)
    x_profile = torch.tensor(
        [[0.5, 0.2, 0, 0], [-0.3, 0.1, 1, 1], [0.0, -0.5, 2, 2], [1.0, 0.0, 1, 0]],
        dtype=torch.float32,
    )
    now_lstm = NowLSTM()
    out = now_lstm(x_seq, x_profile)
    n_params = sum(p.numel() for p in now_lstm.parameters())
    print(f"  output shape: {tuple(out.shape)}  (expected [{batch}, {OUTPUT_DIM}])")
    print(f"  parameters: {n_params:,}")

    print("\n[OK] 4 모델 모두 forward 통과")


if __name__ == "__main__":
    _smoke_test()
