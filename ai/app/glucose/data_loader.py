"""PyTorch Dataset / DataLoader.

MealDataset:        Model 1 (식사 시점 → 식후 BG)
TimeseriesDataset:  Model 2 (시계열 BG → 향후 BG)

데이터 담당자가 출력한 CSV/.npz 를 그대로 로드.
컬럼/shape 명세는 DATA_SPEC.md 참고.
"""

from __future__ import annotations

import pickle
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
from torch.utils.data import DataLoader, Dataset

from app.glucose.constants import (
    DEFAULT_SCALER_PATH,
    LABEL_COLS,
    MEAL_CATEGORICAL_COLS,
    MEAL_CONTINUOUS_COLS,
)


# ─────────────────────────────────────────────────────────────────────
# Model 1
# ─────────────────────────────────────────────────────────────────────


class MealDataset(Dataset):
    """processed/{train,val,test}.csv 로드.

    각 행 = 한 식사 이벤트.
    return_categorical_separately:
        False → ((X[9],), y[24])     # 카테고리 정수 그대로 concat (Ridge 등)
        True  → ((X_cont[6], X_cat[3]), y[24])  # 임베딩 모델용
    """

    def __init__(
        self,
        csv_path: str | Path,
        return_categorical_separately: bool = True,
    ) -> None:
        self.csv_path = Path(csv_path)
        if not self.csv_path.exists():
            raise FileNotFoundError(f"{csv_path} 가 없습니다. 데이터 담당자 확인.")
        df = pd.read_csv(self.csv_path)
        self._validate_columns(df)

        self.return_separately = return_categorical_separately
        self.x_continuous = df[MEAL_CONTINUOUS_COLS].to_numpy(dtype=np.float32)
        self.x_categorical = df[MEAL_CATEGORICAL_COLS].to_numpy(dtype=np.int64)
        self.y = df[LABEL_COLS].to_numpy(dtype=np.float32)

    @staticmethod
    def _validate_columns(df: pd.DataFrame) -> None:
        expected = MEAL_CONTINUOUS_COLS + MEAL_CATEGORICAL_COLS + LABEL_COLS
        missing = [c for c in expected if c not in df.columns]
        if missing:
            raise ValueError(
                f"CSV 컬럼 누락: {missing}. DATA_SPEC.md 와 일치하는지 확인."
            )

    def __len__(self) -> int:
        return len(self.y)

    def __getitem__(self, idx: int) -> Any:
        x_cont = torch.from_numpy(self.x_continuous[idx])
        x_cat = torch.from_numpy(self.x_categorical[idx])
        y = torch.from_numpy(self.y[idx])
        if self.return_separately:
            return (x_cont, x_cat), y
        # 카테고리 정수를 float로 캐스팅 후 concat
        x = torch.cat([x_cont, x_cat.float()], dim=0)
        return x, y


# ─────────────────────────────────────────────────────────────────────
# Model 2
# ─────────────────────────────────────────────────────────────────────


class TimeseriesDataset(Dataset):
    """processed/timeseries/{train,val,test}.npz 로드.

    .npz 키:
        X_seq:     [N, 12]  — 정규화된 BG 시계열
        X_profile: [N, 4]   — [weight_kg_norm, fasting_bg_norm, activity, diabetes_type]
        y:         [N, 24]  — 정규화된 향후 BG
    """

    def __init__(self, npz_path: str | Path) -> None:
        self.npz_path = Path(npz_path)
        if not self.npz_path.exists():
            raise FileNotFoundError(f"{npz_path} 가 없습니다. 데이터 담당자 확인.")
        data = np.load(self.npz_path)
        for key in ("X_seq", "X_profile", "y"):
            if key not in data:
                raise ValueError(f".npz 키 누락: {key}")
        self.x_seq = data["X_seq"].astype(np.float32)
        self.x_profile = data["X_profile"].astype(np.float32)
        self.y = data["y"].astype(np.float32)
        self._validate_shapes()

    def _validate_shapes(self) -> None:
        n = len(self.y)
        assert self.x_seq.shape == (n, 12), f"X_seq shape mismatch: {self.x_seq.shape}"
        assert self.x_profile.shape == (n, 4), f"X_profile shape mismatch: {self.x_profile.shape}"
        assert self.y.shape == (n, 24), f"y shape mismatch: {self.y.shape}"

    def __len__(self) -> int:
        return len(self.y)

    def __getitem__(self, idx: int) -> Any:
        x_seq = torch.from_numpy(self.x_seq[idx])
        x_profile = torch.from_numpy(self.x_profile[idx])
        y = torch.from_numpy(self.y[idx])
        return (x_seq, x_profile), y


# ─────────────────────────────────────────────────────────────────────
# 헬퍼
# ─────────────────────────────────────────────────────────────────────


def get_dataloader(
    dataset: Dataset,
    batch_size: int = 64,
    shuffle: bool = True,
    num_workers: int = 0,
) -> DataLoader:
    return DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=shuffle,
        num_workers=num_workers,
        pin_memory=torch.cuda.is_available(),
    )


def load_scaler(path: str | Path = DEFAULT_SCALER_PATH) -> dict[str, Any]:
    """scaler.pkl 로드. dict 구조 검증.

    기대 키: model1_features, bg_target, profile (DATA_SPEC.md 참고)
    """
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"{path} 가 없습니다. 데이터 담당자가 생성.")
    with open(path, "rb") as f:
        scaler = pickle.load(f)
    if not isinstance(scaler, dict):
        raise ValueError(f"scaler.pkl 이 dict 가 아님: {type(scaler)}")
    expected_keys = {"model1_features", "bg_target", "profile"}
    missing = expected_keys - set(scaler.keys())
    if missing:
        raise ValueError(f"scaler.pkl 키 누락: {missing}")
    return scaler
