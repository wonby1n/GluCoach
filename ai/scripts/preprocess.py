"""
혈당 예측 모델 학습용 전처리 스크립트 (Model 1)

입력:  sim_v3 구조 — data_dir 아래 카테고리별 서브폴더
       또는 구버전 단일 폴더 (users.csv / meal_events.csv / glucose_readings.csv 직접 보유)
출력:  ai/data/processed/{train,val,test}.csv   ← Model 1용 (33컬럼)
       ai/models/scaler.pkl                      ← dict 형태
       ai/models/user_split.json                 ← 환자 분할 메타

사용법 (ai/ 폴더 기준):
    python scripts/preprocess.py
    python scripts/preprocess.py --data-dir data/glucose_data/sim_data/sim_v3
    python scripts/preprocess.py --max-categories 10   # 빠른 테스트용
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

FEATURE_COLS = [
    "carbs", "meal_time_sin", "meal_time_cos", "current_glucose",
    "fasting_bg", "weight_kg", "activity", "diabetes_type", "meal_pattern",
]
MODEL1_FEATURE_SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]


# ── 1. 데이터 로드 ────────────────────────────────────────────────────────────

def load_data(data_dir: Path, max_categories: Optional[int] = None):
    """
    sim_v3 구조(서브폴더 108개) 또는 단일 폴더 모두 지원.
    max_categories: 테스트용 — 처음 N개 카테고리만 로드.
    """
    subdirs = sorted([d for d in data_dir.iterdir() if d.is_dir()])

    if subdirs and (subdirs[0] / "users.csv").exists():
        # sim_v3 구조: 카테고리별 서브폴더
        if max_categories:
            subdirs = subdirs[:max_categories]
        print(f"   멀티 디렉토리 모드: {len(subdirs)}개 카테고리 로드 중...")
        users_list, meals_list, glucose_list = [], [], []
        for subdir in subdirs:
            users_list.append(pd.read_csv(subdir / "users.csv"))
            meals_list.append(pd.read_csv(subdir / "meal_events.csv"))
            glucose_list.append(pd.read_csv(subdir / "glucose_readings.csv"))
        users = pd.concat(users_list, ignore_index=True)
        meals = pd.concat(meals_list, ignore_index=True)
        glucose = pd.concat(glucose_list, ignore_index=True)
    else:
        # 단일 폴더 구조 (구버전 호환)
        print("   단일 디렉토리 모드")
        users = pd.read_csv(data_dir / "users.csv")
        meals = pd.read_csv(data_dir / "meal_events.csv")
        glucose = pd.read_csv(data_dir / "glucose_readings.csv")

    meals["time"] = pd.to_datetime(meals["time"])
    glucose["time"] = pd.to_datetime(glucose["time"])
    return users, meals, glucose


# ── 2. glucose forward fill + 인덱스 변환 ─────────────────────────────────────

def build_glucose_index(glucose: pd.DataFrame) -> dict[str, pd.Series]:
    """
    유저별 5분 grid ffill 후 시간 인덱스 Series dict 반환.
    조회 속도: O(log n) — 선형 탐색 대비 ~100배 빠름.
    """
    result = {}
    for user_id, grp in glucose.groupby("user_id"):
        grp = grp.sort_values("time").set_index("time")["glucose"]
        full_index = pd.date_range(grp.index.min(), grp.index.max(), freq="5min")
        grp = grp.reindex(full_index).ffill()
        result[user_id] = grp
    return result


def get_glucose_at(
    user_series: pd.Series,
    target_time,
    tolerance_sec: int = 150,
) -> Optional[float]:
    """시간 인덱스 Series에서 target_time ±tolerance_sec 이내 가장 가까운 값 반환."""
    idx = user_series.index.get_indexer([target_time], method="nearest")[0]
    if idx < 0:
        return None
    nearest_time = user_series.index[idx]
    if abs((nearest_time - target_time).total_seconds()) > tolerance_sec:
        return None
    return float(user_series.iloc[idx])


# ── 3. 레코드 생성 ─────────────────────────────────────────────────────────────

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

    print("   glucose 인덱스 구축 중...")
    glucose_by_user = build_glucose_index(glucose)

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

        current_glucose = get_glucose_at(ug, meal_time)
        if current_glucose is None:
            skipped += 1
            continue

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


# ── 4. 인코딩 ─────────────────────────────────────────────────────────────────

def encode(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["diabetes_type"] = df["diabetes_type"].map(DIABETES_TYPE_MAP)
    df["activity"] = df["activity"].map(ACTIVITY_MAP)
    df["meal_pattern"] = df["meal_pattern"].map(MEAL_PATTERN_MAP)
    return df


# ── 5. 환자 단위 분할 ─────────────────────────────────────────────────────────

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


# ── 6. 정규화 + scaler 저장 ───────────────────────────────────────────────────

def normalize(train, val, test, models_dir: Path):
    scaler_features = StandardScaler()
    scaler_bg = StandardScaler()
    scaler_profile = StandardScaler()

    train = train.copy()
    val = val.copy()
    test = test.copy()

    train[MODEL1_FEATURE_SCALE_COLS] = scaler_features.fit_transform(train[MODEL1_FEATURE_SCALE_COLS])
    val[MODEL1_FEATURE_SCALE_COLS] = scaler_features.transform(val[MODEL1_FEATURE_SCALE_COLS])
    test[MODEL1_FEATURE_SCALE_COLS] = scaler_features.transform(test[MODEL1_FEATURE_SCALE_COLS])

    scaler_profile.fit(train[["weight_kg", "fasting_bg"]].values)

    # BG 라벨 24개 flatten 통합 fit
    train_bg_flat = train[LABEL_COLS].values.reshape(-1, 1)
    scaler_bg.fit(train_bg_flat)
    train[LABEL_COLS] = scaler_bg.transform(train[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))
    val[LABEL_COLS] = scaler_bg.transform(val[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))
    test[LABEL_COLS] = scaler_bg.transform(test[LABEL_COLS].values.reshape(-1, 1)).reshape(-1, len(LABEL_COLS))

    scaler_dict = {
        "model1_features": scaler_features,
        "bg_target": scaler_bg,
        "profile": scaler_profile,
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
    users, meals, glucose = load_data(data_dir, max_categories=args.max_categories)
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

    models_dir.mkdir(parents=True, exist_ok=True)
    with open(models_dir / "user_split.json", "w") as f:
        json.dump(user_split, f, indent=2)
    print(f"   user_split 저장: {models_dir / 'user_split.json'}")

    print("Step 5. 정규화 및 scaler 저장")
    train, val, test = normalize(train, val, test, models_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    train.drop(columns=["user_id"]).to_csv(output_dir / "train.csv", index=False)
    val.drop(columns=["user_id"]).to_csv(output_dir / "val.csv", index=False)
    test.drop(columns=["user_id"]).to_csv(output_dir / "test.csv", index=False)

    # user_id별 split 정보 포함 버전도 별도 저장 (평가/개인화용)
    train.to_csv(output_dir / "train_with_uid.csv", index=False)
    val.to_csv(output_dir / "val_with_uid.csv", index=False)
    test.to_csv(output_dir / "test_with_uid.csv", index=False)

    print(f"Step 6. 저장 완료: {output_dir}")
    print(f"   총 컬럼: {len(FEATURE_COLS) + len(LABEL_COLS)}개  (feature 9 + label 24)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="혈당 예측 모델 전처리 (sim_v3)")
    parser.add_argument(
        "--data-dir",
        default="data/glucose_data/sim_data/sim_v3",
        help="시뮬레이터 데이터 폴더 (서브폴더 구조 또는 단일 폴더)",
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
    parser.add_argument(
        "--max-categories",
        type=int,
        default=None,
        help="테스트용: 처음 N개 카테고리만 처리",
    )
    main(parser.parse_args())
