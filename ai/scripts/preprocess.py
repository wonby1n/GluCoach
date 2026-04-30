"""
혈당 예측 모델 학습용 전처리 스크립트

입력:  simuldate_params/simulate/ 폴더의 세 CSV
출력:  ai/data/processed/{train,val,test}.csv   ← Model 1용 (33컬럼)
       ai/models/scaler.pkl                      ← dict 형태
       ai/models/user_split.json                 ← 환자 분할 메타

사용법 (ai/ 폴더 기준):
    python scripts/preprocess.py
    python scripts/preprocess.py --data-dir <경로>
"""

import argparse
import json
import pickle
from pathlib import Path
from typing import Optional

import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler

# ── 인코딩 맵 ────────────────────────────────────────────────────────────────

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

# ── 컬럼 정의 ─────────────────────────────────────────────────────────────────

LABEL_STEPS = list(range(5, 125, 5))               # [5, 10, ..., 120]
LABEL_COLS = [f"BG_{t}min" for t in LABEL_STEPS]   # 24개

# meal_time은 sin/cos 두 컬럼으로 대체 (정규화 불필요)
FEATURE_COLS = [
    "carbs", "meal_time_sin", "meal_time_cos", "current_glucose",
    "fasting_bg", "weight_kg", "activity", "diabetes_type", "meal_pattern",
]

# z-score 정규화 대상 (meal_time_sin/cos, 정수 인코딩 컬럼 제외)
MODEL1_FEATURE_SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]


# ── 1. 데이터 로드 ────────────────────────────────────────────────────────────

def load_data(data_dir: Path):
    users = pd.read_csv(data_dir / "users.csv")
    meals = pd.read_csv(data_dir / "meal_events.csv")
    glucose = pd.read_csv(data_dir / "glucose_readings.csv")

    meals["time"] = pd.to_datetime(meals["time"])
    glucose["time"] = pd.to_datetime(glucose["time"])

    return users, meals, glucose


# ── 2. glucose forward fill ───────────────────────────────────────────────────

def apply_forward_fill(glucose: pd.DataFrame) -> pd.DataFrame:
    """유저별 5분 간격 glucose 시계열에서 빠진 값을 앞의 값으로 채움"""
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


# ── 3. snapshot 추출 헬퍼 ──────────────────────────────────────────────────────

def get_glucose_at(
    user_glucose: pd.DataFrame,
    target_time,
    tolerance_sec: int = 150,
) -> Optional[float]:
    """target_time 기준 tolerance 내 가장 가까운 glucose 반환. 없으면 None"""
    diff = (user_glucose["time"] - target_time).abs()
    min_diff = diff.min()
    if min_diff.total_seconds() > tolerance_sec:
        return None
    return float(user_glucose.loc[diff.idxmin(), "glucose"])


# ── 4. 레코드 생성 (세 파일 병합 + snapshot) ───────────────────────────────────

def build_records(users: pd.DataFrame, meals: pd.DataFrame, glucose: pd.DataFrame) -> pd.DataFrame:
    """
    meal_event 1건 → 레코드 1건
      입력 9개: carbs, meal_time_sin, meal_time_cos, current_glucose,
               fasting_bg, weight_kg, activity, diabetes_type, meal_pattern
      출력 24개: BG_5min ~ BG_120min
    """
    user_features = (
        users[["user_id", "diabetes_type", "weight_kg", "fasting_bg", "activity", "meal_pattern"]]
        .set_index("user_id")
    )

    glucose = apply_forward_fill(glucose)
    glucose_by_user = {uid: grp.reset_index(drop=True) for uid, grp in glucose.groupby("user_id")}

    records = []
    skipped = 0

    for _, meal in meals.iterrows():
        user_id = meal["user_id"]
        meal_time = meal["time"]
        carbs = float(meal["carbs"])

        if user_id not in glucose_by_user or user_id not in user_features.index:
            skipped += 1
            continue

        ug = glucose_by_user[user_id]

        # 식사 시점 혈당
        current_glucose = get_glucose_at(ug, meal_time)
        if current_glucose is None:
            skipped += 1
            continue

        # 식사 후 5분 ~ 120분 혈당 (120분치 없으면 제외)
        labels = {}
        valid = True
        for t in LABEL_STEPS:
            bg = get_glucose_at(ug, meal_time + pd.Timedelta(minutes=t))
            if bg is None:
                valid = False
                break
            labels[f"BG_{t}min"] = bg

        if not valid:
            skipped += 1
            continue

        # meal_time → sin/cos (circular encoding)
        hour_float = meal_time.hour + meal_time.minute / 60.0
        meal_time_sin = np.sin(2 * np.pi * hour_float / 24)
        meal_time_cos = np.cos(2 * np.pi * hour_float / 24)

        uf = user_features.loc[user_id]
        record = {
            "user_id": user_id,
            "carbs": carbs,
            "meal_time_sin": meal_time_sin,
            "meal_time_cos": meal_time_cos,
            "current_glucose": current_glucose,
            "fasting_bg": float(uf["fasting_bg"]),
            "weight_kg": float(uf["weight_kg"]),
            "activity": uf["activity"],
            "diabetes_type": uf["diabetes_type"],
            "meal_pattern": uf["meal_pattern"],
        }
        record.update(labels)
        records.append(record)

    print(f"   유효 레코드: {len(records)}건  |  제외: {skipped}건")
    return pd.DataFrame(records)


# ── 5. 인코딩 ─────────────────────────────────────────────────────────────────

def encode(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["diabetes_type"] = df["diabetes_type"].map(DIABETES_TYPE_MAP)
    df["activity"] = df["activity"].map(ACTIVITY_MAP)
    df["meal_pattern"] = df["meal_pattern"].map(MEAL_PATTERN_MAP)
    return df


# ── 6. 환자 단위 분할 ─────────────────────────────────────────────────────────

def split_by_patient(df: pd.DataFrame, train_ratio=0.7, val_ratio=0.15, seed=42):
    user_ids = np.array(df["user_id"].unique())
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

    return train, val, test, {"train": train_users, "val": val_users, "test": test_users}


# ── 7. 정규화 + scaler 저장 ───────────────────────────────────────────────────

def normalize(train, val, test, models_dir: Path):
    # Model 1 feature scaler (carbs, current_glucose, fasting_bg, weight_kg)
    scaler_features = StandardScaler()
    # BG 라벨 scaler (BG_5min ~ BG_120min 24개 통합)
    scaler_bg = StandardScaler()
    # Model 2 profile scaler (weight_kg, fasting_bg)
    scaler_profile = StandardScaler()
    scaler_profile.fit(train[["weight_kg", "fasting_bg"]].values)

    train = train.copy()
    val = val.copy()
    test = test.copy()

    # feature 정규화
    train[MODEL1_FEATURE_SCALE_COLS] = scaler_features.fit_transform(train[MODEL1_FEATURE_SCALE_COLS])
    val[MODEL1_FEATURE_SCALE_COLS] = scaler_features.transform(val[MODEL1_FEATURE_SCALE_COLS])
    test[MODEL1_FEATURE_SCALE_COLS] = scaler_features.transform(test[MODEL1_FEATURE_SCALE_COLS])

    # BG 라벨 정규화 (24개 flatten 통합 fit)
    train_bg_flat = train[LABEL_COLS].values.reshape(-1, 1)
    scaler_bg.fit(train_bg_flat)

    train[LABEL_COLS] = scaler_bg.transform(train[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))
    val[LABEL_COLS] = scaler_bg.transform(val[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))
    test[LABEL_COLS] = scaler_bg.transform(test[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))

    # scaler dict 저장
    scaler_dict = {
        "model1_features": scaler_features,  # carbs, current_glucose, fasting_bg, weight_kg
        "bg_target": scaler_bg,              # BG 24개 통합
        "profile": scaler_profile,           # weight_kg, fasting_bg (Model 2용)
    }
    models_dir.mkdir(parents=True, exist_ok=True)
    with open(models_dir / "scaler.pkl", "wb") as f:
        pickle.dump(scaler_dict, f)
    print(f"   scaler 저장: {models_dir / 'scaler.pkl'}")

    return train, val, test


# ── main ──────────────────────────────────────────────────────────────────────

def main(args):
    data_dir = Path(args.data_dir)
    output_dir = Path(args.output_dir)
    models_dir = Path(args.models_dir)

    print("Step 1. 데이터 로드")
    users, meals, glucose = load_data(data_dir)
    print(f"   users={len(users)}명  meals={len(meals)}건  glucose={len(glucose)}건")

    print("Step 2. 레코드 생성 (forward fill + snapshot 추출)")
    df = build_records(users, meals, glucose)

    if df.empty:
        print("유효 레코드가 없습니다. 데이터 경로 및 형식을 확인하세요.")
        return

    print("Step 3. 인코딩")
    df = encode(df)

    print("Step 4. 환자 단위 분할 (train 70% / val 15% / test 15%)")
    train, val, test, user_split = split_by_patient(df)
    print(f"   train={len(train)}건  val={len(val)}건  test={len(test)}건")

    # user_split.json 저장
    models_dir.mkdir(parents=True, exist_ok=True)
    with open(models_dir / "user_split.json", "w") as f:
        json.dump(user_split, f, indent=2)
    print(f"   user_split 저장: {models_dir / 'user_split.json'}")

    print("Step 5. 정규화 및 scaler 저장")
    train, val, test = normalize(train, val, test, models_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    train.to_csv(output_dir / "train.csv", index=False)
    val.to_csv(output_dir / "val.csv", index=False)
    test.to_csv(output_dir / "test.csv", index=False)

    print(f"Step 6. 저장 완료: {output_dir}")
    print(f"   feature 컬럼 (9개): {FEATURE_COLS}")
    print(f"   label  컬럼 (24개): BG_5min ~ BG_120min  ← z-score 정규화 적용")
    print(f"   총 컬럼: {len(FEATURE_COLS) + len(LABEL_COLS)}개")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="혈당 예측 모델 전처리")
    parser.add_argument(
        "--data-dir",
        default="../../simuldate_params/simulate",
        help="시뮬레이터 데이터 폴더 경로",
    )
    parser.add_argument(
        "--output-dir",
        default="data/processed",
        help="전처리 결과 저장 경로",
    )
    parser.add_argument(
        "--models-dir",
        default="models",
        help="scaler, user_split 저장 경로",
    )
    main(parser.parse_args())
