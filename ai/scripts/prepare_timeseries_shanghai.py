"""Shanghai 시계열 → Model 2 학습 데이터 (.npz).

전제 (preprocess_shanghai.py 실행 후):
- ai/models/scaler.pkl       (bg_target, profile 키)
- ai/models/user_split.json  (patient_id 단위 분할)

입력:
- ai/data/glucose_data/Shanghai/Shanghai_T1DM/*.xls(x)
- ai/data/glucose_data/Shanghai/Shanghai_T2DM/*.xls(x)
- ai/data/glucose_data/Shanghai/Shanghai_T{1,2}DM_Summary.xlsx (profile 위해)

출력:
- ai/data/processed/timeseries/{train,val,test}.npz
    X_seq:     [N, 12]  z-score (bg_target)
    X_profile: [N, 4]   = [weight_kg_norm, fasting_bg_norm, activity_int, diabetes_type_int]
    y:         [N, 24]  z-score (bg_target)
    user_ids:  [N]      (patient_id, split 검증용)

알고리즘:
- 환자 파일 → 5분 grid 보간 시계열
- meal_events 시각 set (마스킹용)
- sliding window 36 timestep (60min input + 120min output), stride 6 (30min)
- 윈도우 안에 식사 이벤트 있으면 제외
- patient_id 기준 user_split.json 따라 train/val/test 분배

사용:
    python scripts/prepare_timeseries_shanghai.py
"""

from __future__ import annotations

import argparse
import json
import os
import pickle
import sys
import warnings
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

warnings.filterwarnings("ignore")


# ── 인코딩 매핑 (preprocess.py 와 동기) ─────────────────────────────────

DIABETES_TYPE_MAP = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP = {"low": 0, "medium": 1, "high": 2}
SHANGHAI_TYPE_MAP = {"T1DM": "T1D", "T2DM": "T2D"}

INPUT_LEN = 12       # 60min
OUTPUT_LEN = 24      # 120min
WINDOW_LEN = INPUT_LEN + OUTPUT_LEN  # 36
STRIDE = 6           # 30min

CGM_COL = "CGM (mg / dl)"
TIME_COL = "Date"
MEAL_COL = "Dietary intake"


# ─────────────────────────────────────────────────────────────────────
# Loaders
# ─────────────────────────────────────────────────────────────────────


def load_summaries(t1d_path: Path, t2d_path: Path) -> dict[str, dict[str, Any]]:
    meta: dict[str, dict[str, Any]] = {}
    for path, dtype in [(t1d_path, "T1DM"), (t2d_path, "T2DM")]:
        if not path.exists():
            continue
        df = pd.read_excel(path)
        for _, row in df.iterrows():
            file_id = str(row["Patient Number"]).strip()
            try:
                weight = float(row["Weight (kg)"])
                fbg = float(row["Fasting Plasma Glucose (mg/dl)"])
            except (ValueError, KeyError, TypeError):
                continue
            if np.isnan(weight) or np.isnan(fbg):
                continue
            meta[file_id] = {
                "weight_kg": weight,
                "fasting_bg": fbg,
                "diabetes_type": SHANGHAI_TYPE_MAP[dtype],
            }
    return meta


def list_patient_files(t1d_dir: Path, t2d_dir: Path) -> list[Path]:
    files = []
    for d in [t1d_dir, t2d_dir]:
        if not d.exists():
            continue
        for p in sorted(d.iterdir()):
            if p.name.startswith("~$"):
                continue
            if p.suffix.lower() in (".xls", ".xlsx"):
                files.append(p)
    return files


def extract_patient_id(file_id: str) -> str:
    return file_id.split("_")[0]


def load_scaler(scaler_path: Path) -> dict[str, Any]:
    if not scaler_path.exists():
        raise FileNotFoundError(
            f"{scaler_path} 없음. preprocess_shanghai.py 가 먼저 실행되어야 함."
        )
    with open(scaler_path, "rb") as f:
        scaler = pickle.load(f)
    for k in ("bg_target", "profile"):
        if k not in scaler:
            raise ValueError(f"scaler.pkl 키 누락: {k}")
    return scaler


def load_user_split(path: Path) -> dict[str, list[str]]:
    if not path.exists():
        raise FileNotFoundError(
            f"{path} 없음. preprocess_shanghai.py 가 먼저 실행되어야 함."
        )
    return json.loads(path.read_text(encoding="utf-8"))


# ─────────────────────────────────────────────────────────────────────
# 5분 grid 보간
# ─────────────────────────────────────────────────────────────────────


def reindex_5min_grid(df: pd.DataFrame) -> pd.DataFrame:
    """5분 grid reindex + CGM 선형 보간."""
    df = df[[TIME_COL, CGM_COL]].dropna(subset=[TIME_COL]).copy()
    df = df.sort_values(TIME_COL).set_index(TIME_COL)
    df = df[~df.index.duplicated(keep="first")]
    if len(df) == 0:
        return df.reset_index()

    start = df.index.min().floor("5min")
    end = df.index.max().ceil("5min")
    full_idx = pd.date_range(start, end, freq="5min")
    df = df.reindex(df.index.union(full_idx)).sort_index()
    df[CGM_COL] = df[CGM_COL].interpolate(method="linear")
    df = df.loc[full_idx]
    df.index.name = TIME_COL
    return df.reset_index()


# ─────────────────────────────────────────────────────────────────────
# Sliding window 추출
# ─────────────────────────────────────────────────────────────────────


def extract_meal_times(df: pd.DataFrame) -> set[pd.Timestamp]:
    """식사 시각 set (윈도우 마스킹용). 5분 floor."""
    if MEAL_COL not in df.columns:
        return set()
    meals = df[[TIME_COL, MEAL_COL]].dropna(subset=[MEAL_COL])
    times: set[pd.Timestamp] = set()
    for _, row in meals.iterrows():
        text = str(row[MEAL_COL]).strip()
        if not text or "not available" in text.lower():
            continue
        times.add(pd.Timestamp(row[TIME_COL]).floor("5min"))
    return times


def extract_windows(
    grid_df: pd.DataFrame,
    meal_times: set[pd.Timestamp],
) -> list[tuple[np.ndarray, np.ndarray]]:
    """5분 grid 시계열 → (X_seq[12], y[24]) 윈도우 리스트.

    윈도우 안에 식사 이벤트 있으면 제외.
    """
    if len(grid_df) < WINDOW_LEN:
        return []

    times = grid_df[TIME_COL].to_numpy()
    bgs = grid_df[CGM_COL].to_numpy(dtype=np.float32)

    # NaN 있는 지점은 윈도우에서 제외 (보간 후에도 시작/끝 NaN 가능성)
    samples: list[tuple[np.ndarray, np.ndarray]] = []
    n = len(bgs)
    for start in range(0, n - WINDOW_LEN + 1, STRIDE):
        end = start + WINDOW_LEN
        window = bgs[start:end]
        if np.isnan(window).any():
            continue

        # 윈도우 시간 범위에 식사 있는지
        window_times = times[start:end]
        t_min = pd.Timestamp(window_times[0])
        t_max = pd.Timestamp(window_times[-1])
        has_meal = any(t_min <= mt <= t_max for mt in meal_times)
        if has_meal:
            continue

        x_seq = window[:INPUT_LEN].copy()
        y = window[INPUT_LEN:].copy()
        samples.append((x_seq, y))

    return samples


# ─────────────────────────────────────────────────────────────────────
# Profile 인코딩
# ─────────────────────────────────────────────────────────────────────


def build_profile(meta_entry: dict[str, Any], profile_scaler: Any) -> np.ndarray:
    """Shanghai 메타 → profile [4]: [weight_norm, fasting_norm, activity_int, dtype_int].

    Shanghai 는 activity 정보 없음 → 0 (low) 고정.
    """
    weight = float(meta_entry["weight_kg"])
    fbg = float(meta_entry["fasting_bg"])
    raw = np.array([[weight, fbg]], dtype=np.float32)
    scaled = profile_scaler.transform(raw)[0]  # [2]

    activity_int = ACTIVITY_MAP["low"]
    dtype_int = DIABETES_TYPE_MAP[meta_entry["diabetes_type"]]
    return np.array(
        [scaled[0], scaled[1], float(activity_int), float(dtype_int)],
        dtype=np.float32,
    )


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────


def main(args: argparse.Namespace) -> int:
    shanghai_dir = Path(args.shanghai_dir)
    output_dir = Path(args.output_dir)
    scaler_path = Path(args.scaler)
    user_split_path = Path(args.user_split)

    t1d_dir = shanghai_dir / "Shanghai_T1DM"
    t2d_dir = shanghai_dir / "Shanghai_T2DM"
    t1d_summary = shanghai_dir / "Shanghai_T1DM_Summary.xlsx"
    t2d_summary = shanghai_dir / "Shanghai_T2DM_Summary.xlsx"

    print("[Step 1] meta + scaler + user_split 로드")
    meta = load_summaries(t1d_summary, t2d_summary)
    scaler = load_scaler(scaler_path)
    user_split = load_user_split(user_split_path)
    bg_scaler = scaler["bg_target"]
    profile_scaler = scaler["profile"]
    print(f"  meta {len(meta)}, train/val/test 환자 = "
          f"{len(user_split['train'])}/{len(user_split['val'])}/{len(user_split['test'])}")

    print("\n[Step 2] 환자 파일 순회 - sliding window 추출")
    files = list_patient_files(t1d_dir, t2d_dir)
    print(f"  대상 파일 {len(files)}개")

    # patient_id → list of (X_seq, X_profile, y)
    samples_by_patient: dict[str, list[tuple[np.ndarray, np.ndarray, np.ndarray]]] = {}
    skipped = {"no_meta": 0, "missing_columns": 0, "empty_grid": 0, "no_windows": 0}

    for i, path in enumerate(files, 1):
        file_id = path.stem
        patient_id = extract_patient_id(file_id)

        if file_id in meta:
            m = meta[file_id]
        else:
            candidates = [k for k in meta if k.startswith(patient_id + "_")]
            if not candidates:
                skipped["no_meta"] += 1
                continue
            m = meta[candidates[0]]

        df = pd.read_excel(path)
        if CGM_COL not in df.columns:
            skipped["missing_columns"] += 1
            continue

        grid_df = reindex_5min_grid(df)
        if len(grid_df) == 0:
            skipped["empty_grid"] += 1
            continue

        meal_times = extract_meal_times(df)
        windows = extract_windows(grid_df, meal_times)
        if not windows:
            skipped["no_windows"] += 1
            continue

        profile = build_profile(m, profile_scaler)
        samples = [(x_seq, profile, y) for (x_seq, y) in windows]
        samples_by_patient.setdefault(patient_id, []).extend(samples)

        if i % 20 == 0 or i == len(files):
            total = sum(len(v) for v in samples_by_patient.values())
            print(f"  [{i}/{len(files)}] 누적 윈도우 {total}")

    print("\n  [skip 통계]")
    for k, v in skipped.items():
        print(f"    {k}: {v}")

    total_windows = sum(len(v) for v in samples_by_patient.values())
    if total_windows == 0:
        print("\n[ERROR] 유효 윈도우 0건. STRIDE / 식사 빈도 확인.")
        return 1

    print(f"\n  총 윈도우: {total_windows}")
    print(f"  환자 수: {len(samples_by_patient)}")

    print("\n[Step 3] BG 정규화")
    # 한 번에 stack 후 transform
    for pid, lst in samples_by_patient.items():
        new_lst = []
        for x_seq, x_profile, y in lst:
            x_seq_n = bg_scaler.transform(x_seq.reshape(-1, 1)).flatten().astype(np.float32)
            y_n = bg_scaler.transform(y.reshape(-1, 1)).flatten().astype(np.float32)
            new_lst.append((x_seq_n, x_profile, y_n))
        samples_by_patient[pid] = new_lst

    print("\n[Step 4] split 분배 (user_split.json 기준)")
    output_dir.mkdir(parents=True, exist_ok=True)
    counts: dict[str, int] = {}
    for split in ("train", "val", "test"):
        user_set = set(user_split[split])
        sub: list[tuple[np.ndarray, np.ndarray, np.ndarray, str]] = []
        for pid in user_set:
            if pid in samples_by_patient:
                for x_seq, x_profile, y in samples_by_patient[pid]:
                    sub.append((x_seq, x_profile, y, pid))
        if not sub:
            print(f"  [WARNING] {split}: 0 sample")
            counts[split] = 0
            continue

        x_seq_arr = np.stack([s[0] for s in sub], axis=0)
        x_profile_arr = np.stack([s[1] for s in sub], axis=0)
        y_arr = np.stack([s[2] for s in sub], axis=0)
        user_ids_arr = np.array([s[3] for s in sub])

        out_path = output_dir / f"{split}.npz"
        np.savez_compressed(
            out_path,
            X_seq=x_seq_arr,
            X_profile=x_profile_arr,
            y=y_arr,
            user_ids=user_ids_arr,
        )
        counts[split] = len(sub)
        print(f"  {split}: {len(sub)} 윈도우 -> {out_path}")

    print("\n[Step 5] split 무결성 검증")
    for split, n in counts.items():
        if n == 0:
            continue
        loaded = np.load(output_dir / f"{split}.npz")
        actual_users = set(loaded["user_ids"].tolist())
        expected = set(user_split[split])
        leaks = actual_users - expected
        if leaks:
            print(f"  [ERROR] {split} leakage: {leaks}")
            return 1
        print(f"  [OK] {split}: {n} 윈도우, 환자 {len(actual_users)}명, leakage 0")

    print("\n[완료]")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Shanghai → Model 2 시계열 데이터")
    parser.add_argument("--shanghai-dir", default="data/glucose_data/Shanghai")
    parser.add_argument("--output-dir", default="data/processed/timeseries")
    parser.add_argument("--scaler", default="models/scaler.pkl")
    parser.add_argument("--user-split", default="models/user_split.json")
    args = parser.parse_args()
    os.chdir(Path(__file__).resolve().parent.parent)
    sys.exit(main(args))
