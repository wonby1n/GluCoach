"""학습 reproducibility 보장용 seed 고정."""

from __future__ import annotations

import os
import random

import numpy as np
import torch


SEED = 42


def set_seed(seed: int = SEED, deterministic: bool = False) -> None:
    """모든 RNG seed 고정.

    deterministic=True 면 cuDNN 도 결정적 (성능 일부 희생).
    """
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    if deterministic:
        torch.backends.cudnn.deterministic = True
        torch.backends.cudnn.benchmark = False
