"""Model 2 시계열 데이터 추출 스크립트.

전제:
    ai/models/scaler.pkl       (preprocess.py 가 생성)
    ai/models/user_split.json  (preprocess.py 가 생성)

출력:
    ai/data/processed/timeseries/{train,val,test}.npz
        X_seq:     [N, 12]  z-score (bg_target)
        X_profile: [N, 4]   = [weight_kg_norm, fasting_bg_norm, activity_int, diabetes_type_int]
        y:         [N, 24]  z-score (bg_target)
        user_ids:  [N]      메타

알고리즘:
- 유저별 5분 grid reindex + ffill
- sliding window: 36 timestep (60min input + 120min output), stride 6 timestep (30min)
- 윈도우 안에 식사 이벤트 시각이 있으면 제외

사용법:
    cd ai/
    python scripts/prepare_timeseries.py
    python scripts/prepare_timeseries.py --max-categories 5  # 빠른 테스트용
"""

from __future__ import annotations

import argparse
import json
import os
import pickle
import sys
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

# 인코딩 매핑 (preprocess.py 와 동기)
DIABETES_TYPE_MAP = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP = {"low": 0, "medium": 1, "high": 2}

INPUT_LEN = 12   # 60min (5분 간격)
OUTPUT_LEN = 24  # 120min
WINDOW_LEN = INPUT_LEN + OUTPUT_LEN  # 36
STRIDE = 6        # 30min


# ─────────────────────────────────────────────────────────────────────
# Loaders
# ─────────────────────────────────────────────────────────────────────


def load_data(
    data_dir: Path,
    max_categories: int | None = None,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """sim_v3 서브폴더 구조 또는 단일 폴더 모두 지원."""
    subdirs = sorted([d for d in data_dir.iterdir() if d.is_dir()])

    if subdirs and (subdirs[0] / "users.csv").exists():
        if max_categories:
            subdirs = subdirs[:max_categories]
        print(f"  멀티 디렉토리 모드: {len(subdirs)}개 카테고리")
        users_list, meals_list, glucose_list = [], [], []
        for subdir in subdirs:
            users_list.append(pd.read_csv(subdir / "users.csv"))
            meals_list.append(pd.read_csv(subdir / "meal_events.csv"))
            glucose_list.append(pd.read_csv(subdir / "glucose_readings.csv"))
        users = pd.concat(users_list, ignore_index=True)
        meals = pd.concat(meals_list, ignore_index=True)
        glucose = pd.concat(glucose_list, ignore_index=True)
    else:
        print("  단일 디렉토리 모드")
        users = pd.read_csv(data_dir / "users.csv")
        meals = pd.read_csv(data_dir / "meal_events.csv")
        glucose = pd.read_csv(data_dir / "glucose_readings.csv")

    meals["time"] = pd.to_datetime(meals["time"])
    glucose["time"] = pd.to_datetime(glucose["time"])
    return users, meals, glucose


def load_scaler(scaler_path: Path) -> dict[str, Any]:
    if not scaler_path.exists():
        raise FileNotFoundError(
            f"{scaler_path} 없음. preprocess.py 가 먼저 실행되어 scaler.pkl 을 생성해야 함."
        )
    with open(scaler_path, "rb") as f:
        scaler = pickle.load(f)
    if not isinstance(scaler, dict):
        raise ValueError(f"scaler.pkl 이 dict 가 아님: {type(scaler)}")
    for k in ("bg_target", "profile"):
        if k not in scaler:
            raise ValueError(f"scaler.pkl 키 누락: {k}")
    return scaler


def load_user_split(path: Path) -> dict[str, list[str]]:
    if not path.exists():
        raise FileNotFoundError(
            f"{path} 없음. preprocess.py 가 먼저 실행되어 user_split.json 을 생성해야 함."
        )
    return json.loads(path.read_text(encoding="utf-8"))


# ─────────────────────────────────────────────────────────────────────
# Preprocessing
# ─────────────────────────────────────────────────────────────────────


def apply_forward_fill(glucose: pd.DataFrame) -> pd.DataFrame:
    """유저별 5분 grid reindex + ffill."""
    filled = []
    for user_id, grp in glucose.groupby("user_id"):
        grp = grp.sort_values("time").set_index("time")
        full_index = pd.date_range(grp.index.min(), grp.index.max(), freq="5min")
        grp = grp.reindex(full_index).ffill()
        grp.index.name = "time"
        grp["user_id"] = user_id
        grp = grp.reset_index()[["user_id", "time", "glucose"]]
        filled.append(grp)
    return pd.concat(filled, ignore_index=True)


def extract_windows_for_user(
    user_glucose: pd.DataFrame,
    meal_times: set[pd.Timestamp],
    user_id: str,
) -> list[dict[str, Any]]:
    """한 유저의 시계열에서 sliding window 추출. 식사 포함 윈도우는 제외."""
    user_glucose = user_glucose.sort_values("time").reset_index(drop=True)
    bgs = user_glucose["glucose"].to_numpy(dtype=np.float32)
    times = user_glucose["time"].to_numpy()
    n = len(bgs)
    if n < WINDOW_LEN:
        return []

    samples: list[dict[str, Any]] = []
    for start in range(0, n - WINDOW_LEN + 1, STRIDE):
        end = start + WINDOW_LEN
        window_times = times[start:end]
        # 윈도우 시간 범위 안에 식사 이벤트 있는지
        t_min = pd.Timestamp(window_times[0])
        t_max = pd.Timestamp(window_times[-1])
        has_meal = any(t_min <= mt <= t_max for mt in meal_times)
        if has_meal:
            continue
        samples.append(
            {
                "user_id": user_id,
                "X_seq": bgs[start : start + INPUT_LEN].copy(),
                "y": bgs[start + INPUT_LEN : end].copy(),
            }
        )
    return samples


def build_profile(
    user_row: pd.Series,
    profile_scaler: Any,
) -> np.ndarray:
    """유저 1명의 profile [4] 생성."""
    weight_kg = float(user_row["weight_kg"])
    fasting_bg = float(user_row["fasting_bg"])
    activity_str = str(user_row["activity"])
    dtype_str = str(user_row["diabetes_type"])

    if activity_str not in ACTIVITY_MAP:
        raise ValueError(f"unknown activity: {activity_str}")
    if dtype_str not in DIABETES_TYPE_MAP:
        raise ValueError(f"unknown diabetes_type: {dtype_str}")

    # weight_kg, fasting_bg 정규화
    raw = np.array([[weight_kg, fasting_bg]], dtype=np.float32)
    scaled = profile_scaler.transform(raw)[0]  # [2]

    return np.array(
        [scaled[0], scaled[1], float(ACTIVITY_MAP[activity_str]), float(DIABETES_TYPE_MAP[dtype_str])],
        dtype=np.float32,
    )


def normalize_bg_series(arr: np.ndarray, bg_scaler: Any) -> np.ndarray:
    flat = arr.reshape(-1, 1)
    return bg_scaler.transform(flat).reshape(arr.shape)


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────


def main(args: argparse.Namespace) -> int:
    data_dir = Path(args.data_dir)
    output_dir = Path(args.output_dir)
    scaler_path = Path(args.scaler)
    user_split_path = Path(args.user_split)

    print("[Step 1] 데이터 로드")
    users, meals, glucose = load_data(data_dir, max_categories=args.max_categories)
    print(f"  users={len(users)}명  meals={len(meals)}건  glucose={len(glucose)}건")

    print("[Step 2] scaler + user_split 로드")
    scaler = load_scaler(scaler_path)
    user_split = load_user_split(user_split_path)
    print(f"  train={len(user_split['train'])}, val={len(user_split['val'])}, test={len(user_split['test'])}")

    print("[Step 3] glucose forward fill")
    glucose = apply_forward_fill(glucose)

    print("[Step 4] 유저별 sliding window 추출")
    glucose_by_user = {uid: grp for uid, grp in glucose.groupby("user_id")}
    meals_by_user: dict[str, set[pd.Timestamp]] = {}
    for uid, grp in meals.groupby("user_id"):
        meals_by_user[uid] = set(pd.to_datetime(grp["time"]).tolist())

    user_features = users.set_index("user_id")

    all_samples: list[dict[str, Any]] = []
    skipped_users = 0
    for uid in user_features.index:
        if uid not in glucose_by_user:
            skipped_users += 1
            continue
        meal_set = meals_by_user.get(uid, set())
        samples = extract_windows_for_user(glucose_by_user[uid], meal_set, uid)
        if not samples:
            continue
        try:
            profile = build_profile(user_features.loc[uid], scaler["profile"])
        except ValueError as e:
            print(f"  [WARNING] user {uid} skip: {e}")
            continue
        for s in samples:
            s["X_profile"] = profile
        all_samples.extend(samples)

    print(f"  유효 윈도우: {len(all_samples)}건  (skip user {skipped_users})")
    if not all_samples:
        print("  [ERROR] 유효 윈도우 0건. 데이터 길이 또는 식사 빈도 확인.")
        return 1

    print("[Step 5] BG 정규화")
    for s in all_samples:
        s["X_seq"] = normalize_bg_series(s["X_seq"], scaler["bg_target"]).astype(np.float32)
        s["y"] = normalize_bg_series(s["y"], scaler["bg_target"]).astype(np.float32)

    print("[Step 6] split 분할 + 저장")
    output_dir.mkdir(parents=True, exist_ok=True)
    counts: dict[str, int] = {}
    for split in ("train", "val", "test"):
        user_set = set(user_split[split])
        sub = [s for s in all_samples if s["user_id"] in user_set]
        if not sub:
            print(f"  [WARNING] {split}: 0 sample")
            counts[split] = 0
            continue
        x_seq = np.stack([s["X_seq"] for s in sub], axis=0)
        x_profile = np.stack([s["X_profile"] for s in sub], axis=0)
        y = np.stack([s["y"] for s in sub], axis=0)
        user_ids = np.array([s["user_id"] for s in sub])
        out_path = output_dir / f"{split}.npz"
        np.savez_compressed(
            out_path,
            X_seq=x_seq,
            X_profile=x_profile,
            y=y,
            user_ids=user_ids,
        )
        counts[split] = len(sub)
        print(f"  {split}: {len(sub)} sample -> {out_path}")

    print("\n[검증]")
    for split, n in counts.items():
        print(f"  {split}: {n}")

    print("\n[완료]")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Model 2 시계열 데이터 추출")
    parser.add_argument(
        "--data-dir",
        default="data/glucose_data/sim_data/sim_v3",
        help="시뮬레이터 데이터 폴더 (서브폴더 구조 또는 단일 폴더)",
    )
    parser.add_argument(
        "--max-categories",
        type=int,
        default=None,
        help="테스트용: 처음 N개 카테고리만 처리",
    )
    parser.add_argument(
        "--output-dir",
        default="data/processed/timeseries",
    )
    parser.add_argument(
        "--scaler",
        default="models/scaler.pkl",
        help="preprocess.py 가 만든 scaler",
    )
    parser.add_argument(
        "--user-split",
        default="models/user_split.json",
        help="preprocess.py 가 만든 환자 분할",
    )
    args = parser.parse_args()
    os.chdir(Path(__file__).resolve().parent.parent)  # ai/ 루트로 이동
    sys.exit(main(args))
