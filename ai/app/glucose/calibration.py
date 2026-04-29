"""raw 비침습 신호 → glucose mg/dL 캘리브레이션.

선형 회귀 단순 매핑. 환자별 캘리브레이션 가정.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import TypedDict

import numpy as np
from sklearn.linear_model import LinearRegression


class CalibParams(TypedDict):
    slope: float
    intercept: float
    r2: float


def fit(raw_signals: np.ndarray, ref_glucose: np.ndarray) -> CalibParams:
    """raw → ref glucose 선형 회귀."""
    raw_signals = np.asarray(raw_signals).reshape(-1, 1)
    ref_glucose = np.asarray(ref_glucose).reshape(-1)
    if len(raw_signals) < 2:
        raise ValueError("최소 2 점 필요")
    model = LinearRegression()
    model.fit(raw_signals, ref_glucose)
    pred = model.predict(raw_signals)
    ss_res = float(np.sum((ref_glucose - pred) ** 2))
    ss_tot = float(np.sum((ref_glucose - ref_glucose.mean()) ** 2))
    r2 = 1 - ss_res / ss_tot if ss_tot > 0 else 0.0
    return {
        "slope": float(model.coef_[0]),
        "intercept": float(model.intercept_),
        "r2": float(r2),
    }


def apply(raw_signal: float, params: CalibParams) -> float:
    return params["slope"] * raw_signal + params["intercept"]


def save_params(params: CalibParams, path: str | Path) -> None:
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(params, indent=2), encoding="utf-8")


def load_params(path: str | Path) -> CalibParams:
    return json.loads(Path(path).read_text(encoding="utf-8"))


if __name__ == "__main__":
    rng = np.random.default_rng(42)
    glucose = rng.uniform(70, 250, size=50)
    raw = glucose * 0.95 + rng.normal(0, 5, size=50)
    params = fit(raw, glucose)
    print(f"slope={params['slope']:.3f}, intercept={params['intercept']:.1f}, r2={params['r2']:.3f}")
    print(f"apply(raw=180) → {apply(180, params):.1f} mg/dL")
