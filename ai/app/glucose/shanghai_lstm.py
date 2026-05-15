"""서버 추론용 ShanghaiLSTM 모델 정의.

학습 스크립트(train_stage2_lstm.py)에서 모델 클래스와 prior 계산 함수만
추출한 파일. matplotlib 등 학습 전용 의존성 없음.
"""
from __future__ import annotations

import math

import numpy as np
import torch
import torch.nn as nn

from app.glucose.curve_builder import build_curve
from app.glucose.stage2_model import _glycemic_scale_factor, _linear_ttp_prior

N_FEATURES = 11


class ShanghaiLSTM(nn.Module):
    """식사 정보 → 식후 120분 혈당 변화량(delta) 24-step 예측.

    diabetes_type은 Embedding으로 처리, 나머지 연속 feature는 Linear encoder.
    dtype_idx: feature 벡터에서 diabetes_type의 위치 (기본 8).
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
        n_cont = n_features - 1
        self.dtype_embed = nn.Embedding(3, dtype_emb_dim)
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
        dtype_idx = x[:, self.dtype_idx].long().clamp(0, 2)
        cont = torch.cat([x[:, :self.dtype_idx], x[:, self.dtype_idx + 1:]], dim=1)
        dtype_e = self.dtype_embed(dtype_idx)
        ctx = self.encoder(torch.cat([cont, dtype_e], dim=1))
        h, c = ctx, torch.zeros_like(ctx)
        step_embeds = self.step_embed(torch.arange(self.n_steps, device=x.device))
        outputs = []
        for i in range(self.n_steps):
            step_e = step_embeds[i].unsqueeze(0).expand(ctx.size(0), -1)
            inp = torch.cat([ctx, step_e], dim=1)
            h, c = self.cell(inp, (h, c))
            outputs.append(self.head(h))
        return torch.cat(outputs, dim=1)  # [B, 24]


def compute_prior_delta_row(
    carbs: float,
    protein: float,
    fat: float,
    fiber: float,
    pre_gl: float,
    carb_coef: float = 1.5,
) -> np.ndarray:
    """단일 식사에 대한 선형 prior 혈당 변화량 [24] 계산 (mg/dL).

    음식 영양소 기반으로 예상 혈당 곡선의 뼈대를 계산.
    LSTM은 이 뼈대 대비 잔차(residual)를 예측한다.
    """
    lin_peak = max(0.0, 10.0 + carbs * carb_coef
                   - fiber * 1.50 - fat * 0.30 - protein * 0.20)
    lin_ttp  = _linear_ttp_prior(carbs, fat, fiber, protein)
    scale    = _glycemic_scale_factor(carbs, protein)
    lin_peak = lin_peak * scale
    curve_abs = build_curve(lin_peak, lin_ttp, 3.0, pre_gl)
    return np.array(curve_abs, dtype=np.float32) - pre_gl
