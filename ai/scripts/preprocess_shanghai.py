"""Shanghai T1DM + T2DM → canonical schema (33 컬럼) 변환.

입력:
- ai/data/glucose_data/Shanghai/Shanghai_T1DM/*.xls(x)         (~16 파일)
- ai/data/glucose_data/Shanghai/Shanghai_T1DM_Summary.xlsx
- ai/data/glucose_data/Shanghai/Shanghai_T2DM/*.xls(x)         (~109 파일)
- ai/data/glucose_data/Shanghai/Shanghai_T2DM_Summary.xlsx

출력:
- ai/data/processed/{train,val,test}.csv  (user_id + 33 컬럼)
- ai/models/scaler.pkl                    (dict: model1_features, bg_target, profile)
- ai/models/user_split.json               (patient_id 단위 분할)

핵심 정책:
- 시간 간격 15분 → 5분 grid 선형 보간 (CGM 만)
- multi-visit (1002_0, 1002_1, ...) 같은 환자 → 같은 split 보장
- meal_pattern: 환자별 식사 시각 분포 분석 → 11가지 라벨
- carbs: food_carb_map.py 활용 (영어 텍스트 → carbs g)

사용:
    python scripts/preprocess_shanghai.py
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
from sklearn.preprocessing import StandardScaler

# 같은 scripts/ 폴더의 food_carb_map import
sys.path.insert(0, str(Path(__file__).resolve().parent))
from food_carb_map import map_meal_to_carbs

warnings.filterwarnings("ignore")


# ── 인코딩 매핑 (preprocess.py 와 동기) ─────────────────────────────────

DIABETES_TYPE_MAP = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP = {"low": 0, "medium": 1, "high": 2}
MEAL_PATTERN_MAP = {
    "regular_3": 0,
    "skip_breakfast": 1,
    "skip_dinner": 2,
    "skip_lunch": 3,
    "skip_breakfast_dinner": 4,
    "skip_breakfast_lunch": 5,
    "skip_lunch_dinner": 6,
    "frequent_small": 7,
    "irregular": 8,
    "late_dinner": 9,
    "fasting_day": 10,
}
SHANGHAI_TYPE_MAP = {"T1DM": "T1D", "T2DM": "T2D"}


# ── 컬럼 정의 ─────────────────────────────────────────────────────────

LABEL_STEPS = list(range(5, 125, 5))
LABEL_COLS = [f"BG_{t}min" for t in LABEL_STEPS]

FEATURE_COLS = [
    "carbs", "meal_time_sin", "meal_time_cos", "current_glucose",
    "fasting_bg", "weight_kg", "activity", "diabetes_type", "meal_pattern",
]
MODEL1_SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]
PROFILE_SCALE_COLS = ["weight_kg", "fasting_bg"]

CGM_COL = "CGM (mg / dl)"
TIME_COL = "Date"
MEAL_COL = "Dietary intake"


# ─────────────────────────────────────────────────────────────────────
# Loaders
# ─────────────────────────────────────────────────────────────────────


def load_summaries(t1d_path: Path, t2d_path: Path) -> dict[str, dict[str, Any]]:
    """Summary 두 개 → file_id (Patient Number) 기준 dict."""
    meta: dict[str, dict[str, Any]] = {}
    for path, dtype in [(t1d_path, "T1DM"), (t2d_path, "T2DM")]:
        if not path.exists():
            print(f"  [WARNING] {path} 없음 - skip")
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
    """모든 .xls/.xlsx. Excel 임시 파일(~$ prefix) 제외."""
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
    """'1005_0_20210522' → '1005'"""
    return file_id.split("_")[0]


# ─────────────────────────────────────────────────────────────────────
# 5분 grid 보간
# ─────────────────────────────────────────────────────────────────────


def reindex_5min_grid(df: pd.DataFrame) -> pd.DataFrame:
    """5분 grid reindex + CGM 선형 보간.

    Shanghai 원본은 15분 간격 → 사이를 보간으로 채움.
    """
    df = df[[TIME_COL, CGM_COL]].dropna(subset=[TIME_COL]).copy()
    df = df.sort_values(TIME_COL).set_index(TIME_COL)
    # 중복 timestamp 제거
    df = df[~df.index.duplicated(keep="first")]

    if len(df) == 0:
        return df.reset_index()

    start = df.index.min().floor("5min")
    end = df.index.max().ceil("5min")
    full_idx = pd.date_range(start, end, freq="5min")

    # union 후 보간 → 5분 grid 만 남김
    df = df.reindex(df.index.union(full_idx)).sort_index()
    df[CGM_COL] = df[CGM_COL].interpolate(method="linear")
    df = df.loc[full_idx]
    df.index.name = TIME_COL
    return df.reset_index()


def get_cgm_at(grid_df: pd.DataFrame, target_time: pd.Timestamp) -> float | None:
    """5분 grid 의 정확한 시각 → CGM (없으면 None)."""
    target = pd.Timestamp(target_time).floor("5min")
    matches = grid_df[grid_df[TIME_COL] == target]
    if len(matches) == 0:
        return None
    val = matches.iloc[0][CGM_COL]
    if pd.isna(val):
        return None
    return float(val)


# ─────────────────────────────────────────────────────────────────────
# meal_pattern 추론 (환자별)
# ─────────────────────────────────────────────────────────────────────


def infer_meal_pattern(meal_times: list[pd.Timestamp]) -> str:
    """환자 식사 시각 리스트 → 11가지 패턴 라벨.

    룰:
      - n_meals_per_day 평균 < 1     → fasting_day
      - n_meals_per_day 평균 ≥ 5     → frequent_small
      - 시간대별 빈도 < 0.5/일      → skip 카테고리
      - 야식 비율 > 30%             → late_dinner
      - 시간 std > 5                → irregular
      - 그 외                       → regular_3
    """
    if not meal_times:
        return "fasting_day"

    days = sorted({pd.Timestamp(t).normalize() for t in meal_times})
    n_days = len(days)
    if n_days == 0:
        return "fasting_day"

    n_meals_per_day = len(meal_times) / n_days
    if n_meals_per_day < 1:
        return "fasting_day"
    if n_meals_per_day >= 5:
        return "frequent_small"

    hours = [t.hour for t in meal_times]
    n_breakfast = sum(1 for h in hours if 5 <= h < 11) / n_days
    n_lunch     = sum(1 for h in hours if 11 <= h < 15) / n_days
    n_dinner    = sum(1 for h in hours if 17 <= h < 22) / n_days

    threshold = 0.5
    skips: list[str] = []
    if n_breakfast < threshold:
        skips.append("breakfast")
    if n_lunch < threshold:
        skips.append("lunch")
    if n_dinner < threshold:
        skips.append("dinner")

    if not skips:
        late_ratio = sum(1 for h in hours if h >= 21) / max(len(hours), 1)
        if late_ratio > 0.3:
            return "late_dinner"
        std = float(np.std(hours))
        if std > 5:
            return "irregular"
        return "regular_3"

    s = set(skips)
    if s == {"breakfast"}:
        return "skip_breakfast"
    if s == {"lunch"}:
        return "skip_lunch"
    if s == {"dinner"}:
        return "skip_dinner"
    if s == {"breakfast", "dinner"}:
        return "skip_breakfast_dinner"
    if s == {"breakfast", "lunch"}:
        return "skip_breakfast_lunch"
    if s == {"lunch", "dinner"}:
        return "skip_lunch_dinner"
    return "irregular"


# ─────────────────────────────────────────────────────────────────────
# 환자 파일 → records
# ─────────────────────────────────────────────────────────────────────


def process_patient_file(
    path: Path,
    meta: dict[str, dict[str, Any]],
    skipped_stats: dict[str, int],
    meal_interval_min: int = 120,
) -> list[dict[str, Any]]:
    file_id = path.stem  # "1005_0_20210522"
    patient_id = extract_patient_id(file_id)

    # 메타 매칭: file_id 정확 일치 → 없으면 patient_id prefix 매칭
    if file_id in meta:
        m = meta[file_id]
    else:
        candidates = [k for k in meta if k.startswith(patient_id + "_")]
        if not candidates:
            skipped_stats["no_meta"] += 1
            return []
        m = meta[candidates[0]]

    weight_kg = m["weight_kg"]
    fasting_bg = m["fasting_bg"]
    dtype_str = m["diabetes_type"]

    df = pd.read_excel(path)
    if MEAL_COL not in df.columns or CGM_COL not in df.columns:
        skipped_stats["missing_columns"] += 1
        return []

    # 5분 grid 보간
    grid_df = reindex_5min_grid(df)
    if len(grid_df) == 0:
        skipped_stats["empty_grid"] += 1
        return []

    # 식사 이벤트 추출 (텍스트 + 시각 리스트)
    meals_raw = df[[TIME_COL, MEAL_COL]].dropna(subset=[MEAL_COL])
    meal_times: list[pd.Timestamp] = []
    meal_texts: list[str] = []
    for _, row in meals_raw.iterrows():
        text = str(row[MEAL_COL]).strip()
        if not text or "not available" in text.lower():
            continue
        meal_times.append(pd.Timestamp(row[TIME_COL]))
        meal_texts.append(text)

    if not meal_times:
        skipped_stats["no_meals"] += 1
        return []

    # 환자별 meal_pattern (1개 라벨)
    pattern_str = infer_meal_pattern(meal_times)
    pattern_int = MEAL_PATTERN_MAP[pattern_str]

    # 시간순 정렬 후 다음 식사까지 간격 계산 (이전 식사 오염 방지)
    sorted_pairs = sorted(zip(meal_times, meal_texts), key=lambda p: p[0])
    sorted_times = [p[0] for p in sorted_pairs]
    sorted_texts = [p[1] for p in sorted_pairs]

    records: list[dict[str, Any]] = []
    for idx, (time, text) in enumerate(zip(sorted_times, sorted_texts)):
        # 이전 식사 오염 필터: 다음 식사가 meal_interval_min 분 안에 있으면 제외
        if idx + 1 < len(sorted_times):
            gap = (sorted_times[idx + 1] - time).total_seconds() / 60.0
            if gap < meal_interval_min:
                skipped_stats["meal_overlap"] += 1
                continue

        carbs = map_meal_to_carbs(text)
        if carbs <= 0:
            skipped_stats["zero_carbs"] += 1
            continue

        meal_time = pd.Timestamp(time).floor("5min")

        # 식사 시점 BG
        current_glucose = get_cgm_at(grid_df, meal_time)
        if current_glucose is None:
            skipped_stats["no_current_bg"] += 1
            continue

        # 식후 24개 시점 BG
        bgs: list[float] = []
        valid = True
        for t in LABEL_STEPS:
            bg = get_cgm_at(grid_df, meal_time + pd.Timedelta(minutes=t))
            if bg is None:
                valid = False
                break
            bgs.append(bg)
        if not valid:
            skipped_stats["incomplete_120min"] += 1
            continue

        hour_float = meal_time.hour + meal_time.minute / 60.0
        meal_time_sin = float(np.sin(2 * np.pi * hour_float / 24.0))
        meal_time_cos = float(np.cos(2 * np.pi * hour_float / 24.0))

        record = {
            "user_id": patient_id,  # multi-visit 통합 (앞 4자리)
            "file_id": file_id,     # 디버깅용 (저장 시 drop)
            "carbs": float(carbs),
            "meal_time_sin": meal_time_sin,
            "meal_time_cos": meal_time_cos,
            "current_glucose": current_glucose,
            "fasting_bg": fasting_bg,
            "weight_kg": weight_kg,
            "activity": 0,
            "diabetes_type": DIABETES_TYPE_MAP[dtype_str],
            "meal_pattern": pattern_int,
        }
        for i, t in enumerate(LABEL_STEPS):
            record[f"BG_{t}min"] = bgs[i]
        records.append(record)

    return records


# ─────────────────────────────────────────────────────────────────────
# 환자 단위 split
# ─────────────────────────────────────────────────────────────────────


def split_by_patient(
    df: pd.DataFrame,
    train_ratio: float = 0.7,
    val_ratio: float = 0.15,
    seed: int = 42,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, dict[str, list[str]]]:
    """patient_id 단위 split. multi-visit 자동 같은 split."""
    user_ids = np.array(sorted(df["user_id"].unique()))
    rng = np.random.default_rng(seed)
    rng.shuffle(user_ids)

    n = len(user_ids)
    train_end = int(n * train_ratio)
    val_end = int(n * (train_ratio + val_ratio))

    train_users = user_ids[:train_end].tolist()
    val_users = user_ids[train_end:val_end].tolist()
    test_users = user_ids[val_end:].tolist()

    train = df[df["user_id"].isin(train_users)].copy()
    val = df[df["user_id"].isin(val_users)].copy()
    test = df[df["user_id"].isin(test_users)].copy()

    return train, val, test, {
        "train": train_users,
        "val": val_users,
        "test": test_users,
    }


# ─────────────────────────────────────────────────────────────────────
# 정규화
# ─────────────────────────────────────────────────────────────────────


def normalize(
    train: pd.DataFrame,
    val: pd.DataFrame,
    test: pd.DataFrame,
    models_dir: Path,
    save_scaler: bool = True,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    train = train.copy()
    val = val.copy()
    test = test.copy()

    # 1) Profile scaler (model1_features 적용 전 raw 값으로 fit)
    profile_scaler = StandardScaler()
    profile_scaler.fit(train[PROFILE_SCALE_COLS].values)

    # 2) Model1 features scaler
    feature_scaler = StandardScaler()
    train[MODEL1_SCALE_COLS] = feature_scaler.fit_transform(train[MODEL1_SCALE_COLS])
    val[MODEL1_SCALE_COLS] = feature_scaler.transform(val[MODEL1_SCALE_COLS])
    test[MODEL1_SCALE_COLS] = feature_scaler.transform(test[MODEL1_SCALE_COLS])

    # 3) BG target scaler (24 컬럼 flatten 통합)
    bg_scaler = StandardScaler()
    bg_train_flat = train[LABEL_COLS].values.reshape(-1, 1)
    bg_scaler.fit(bg_train_flat)
    for d in (train, val, test):
        d[LABEL_COLS] = bg_scaler.transform(d[LABEL_COLS].values.reshape(-1, 1)).reshape(
            -1, len(LABEL_COLS)
        )

    scaler_dict = {
        "model1_features": feature_scaler,
        "bg_target": bg_scaler,
        "profile": profile_scaler,
    }
    if save_scaler:
        models_dir.mkdir(parents=True, exist_ok=True)
        with open(models_dir / "scaler.pkl", "wb") as f:
            pickle.dump(scaler_dict, f)

    return train, val, test


# ─────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────


def main(args: argparse.Namespace) -> int:
    shanghai_dir = Path(args.shanghai_dir)
    output_dir = Path(args.output_dir)
    models_dir = Path(args.models_dir)

    t1d_dir = shanghai_dir / "Shanghai_T1DM"
    t2d_dir = shanghai_dir / "Shanghai_T2DM"
    t1d_summary = shanghai_dir / "Shanghai_T1DM_Summary.xlsx"
    t2d_summary = shanghai_dir / "Shanghai_T2DM_Summary.xlsx"

    print("[Step 1] Summary 로드")
    meta = load_summaries(t1d_summary, t2d_summary)
    print(f"  메타 정보 {len(meta)}건")

    print("\n[Step 2] 환자 파일 처리")
    files = list_patient_files(t1d_dir, t2d_dir)
    print(f"  대상 파일 {len(files)}개")

    skipped_stats = {
        "no_meta": 0,
        "missing_columns": 0,
        "empty_grid": 0,
        "no_meals": 0,
        "meal_overlap": 0,
        "zero_carbs": 0,
        "no_current_bg": 0,
        "incomplete_120min": 0,
    }

    all_records: list[dict[str, Any]] = []
    for i, path in enumerate(files, 1):
        records = process_patient_file(path, meta, skipped_stats)
        all_records.extend(records)
        if i % 20 == 0 or i == len(files):
            print(f"  [{i}/{len(files)}] 누적 record {len(all_records)}")

    print("\n  [skip 통계]")
    for k, v in skipped_stats.items():
        print(f"    {k}: {v}")

    if not all_records:
        print("\n[ERROR] 유효 record 0건. 데이터/경로 확인.")
        return 1

    df = pd.DataFrame(all_records)
    print(f"\n[Step 3] 통계")
    print(f"  총 record: {len(df)}")
    print(f"  환자 수 (patient_id 기준): {df['user_id'].nunique()}")
    print(f"  멀티 visit 합산 file 수: {df['file_id'].nunique()}")
    print(f"  diabetes_type 분포: {dict(df.groupby('diabetes_type').size())}")
    print(f"  meal_pattern 분포: {dict(df.groupby('meal_pattern').size())}")
    print(f"  carbs (raw) 통계: mean={df['carbs'].mean():.1f}, std={df['carbs'].std():.1f}, "
          f"min={df['carbs'].min():.1f}, max={df['carbs'].max():.1f}")

    print("\n[Step 4] 환자 단위 split (70/15/15)")
    df_split = df.drop(columns=["file_id"])
    train, val, test, user_split = split_by_patient(df_split)
    print(f"  train: {len(train)} record, {train['user_id'].nunique()}명")
    print(f"  val:   {len(val)} record, {val['user_id'].nunique()}명")
    print(f"  test:  {len(test)} record, {test['user_id'].nunique()}명")

    # disjoint 검증
    tr_set = set(user_split["train"])
    va_set = set(user_split["val"])
    te_set = set(user_split["test"])
    overlaps = (tr_set & va_set) | (tr_set & te_set) | (va_set & te_set)
    if overlaps:
        print(f"  [ERROR] 환자 split overlap: {overlaps}")
        return 1
    print("  [OK] 환자 단위 disjoint OK")

    # user_split.json
    models_dir.mkdir(parents=True, exist_ok=True)
    with open(models_dir / "user_split.json", "w", encoding="utf-8") as f:
        json.dump(user_split, f, indent=2)

    print("\n[Step 5] 정규화 + scaler 저장")
    train, val, test = normalize(train, val, test, models_dir, save_scaler=not args.no_save_scaler)
    if args.no_save_scaler:
        print(f"  scaler 저장 skip (--no-save-scaler)")
    else:
        print(f"  scaler.pkl 키: model1_features, bg_target, profile")

    print("\n[Step 6] CSV 저장")
    output_dir.mkdir(parents=True, exist_ok=True)
    cols_order = ["user_id"] + FEATURE_COLS + LABEL_COLS
    train[cols_order].to_csv(output_dir / "train.csv", index=False)
    val[cols_order].to_csv(output_dir / "val.csv", index=False)
    test[cols_order].to_csv(output_dir / "test.csv", index=False)
    print(f"  컬럼: {len(cols_order)}개 (user_id + {len(FEATURE_COLS)} features + {len(LABEL_COLS)} labels)")

    # 분포 검증
    print("\n[Step 7] 정규화 결과 검증")
    print(f"  features (train) mean ~= 0:")
    for c in MODEL1_SCALE_COLS:
        print(f"    {c}: mean={train[c].mean():+.3f}, std={train[c].std():.3f}")
    bg_flat = train[LABEL_COLS].values.flatten()
    print(f"  BG target (train flat): mean={bg_flat.mean():+.3f}, std={bg_flat.std():.3f}")

    print("\n[완료]")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Shanghai → canonical schema")
    parser.add_argument("--shanghai-dir", default="data/glucose_data/Shanghai")
    parser.add_argument("--output-dir", default="data/processed/shanghai")
    parser.add_argument("--models-dir", default="models/shanghai")
    parser.add_argument("--no-save-scaler", action="store_true",
                        help="scaler.pkl 저장 생략 (기존 scaler 보호용)")
    args = parser.parse_args()
    os.chdir(Path(__file__).resolve().parent.parent)
    sys.exit(main(args))
