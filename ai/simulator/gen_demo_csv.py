"""
시연용 CGM 데이터 생성 스크립트
각 페르소나(NONE/T1D/T2D)당 24시간 분 단위 CGM값 CSV 생성
Output: demo_none.csv / demo_t1d.csv / demo_t2d.csv
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))

from datetime import datetime, timedelta
from simglucose.dataset.simulator_runner import simulate

BASE_DATE = datetime(2024, 1, 15, 0, 0)
SIM_HOURS = 24

# 전형적인 하루 식사 시나리오 (시간, 탄수화물g)
MEALS = [
    (BASE_DATE.replace(hour=7, minute=30),  60),   # 아침
    (BASE_DATE.replace(hour=12, minute=0), 80),    # 점심
    (BASE_DATE.replace(hour=19, minute=0), 70),    # 저녁
]

PERSONAS = {
    "none": {
        "diabetes_type": "Normal",
        "birthdate": "1990-05-01",
        "sex": "M",
        "height_cm": 170,
        "weight_kg": 70,
        "treatment": "diet",
        "treatment_start": None,
        "medication": None,
        "diagnosis_years": 0,
        "hba1c": 5.4,
        "fasting_bg": 90.0,
        "activity": "medium",
        "meal_pattern": "three_meals",
        "target_bg": 100,
    },
    "t1d": {
        "diabetes_type": "T1D",
        "birthdate": "1995-03-15",
        "sex": "M",
        "height_cm": 175,
        "weight_kg": 68,
        "treatment": "insulin",
        "treatment_start": "2010-01-01",
        "medication": None,
        "diagnosis_years": 14,
        "hba1c": 7.8,
        "fasting_bg": 130.0,
        "activity": "medium",
        "meal_pattern": "three_meals",
        "target_bg": 120,
    },
    "t2d": {
        "diabetes_type": "T2D",
        "birthdate": "1970-08-20",
        "sex": "M",
        "height_cm": 168,
        "weight_kg": 85,
        "treatment": "medication",
        "treatment_start": "2015-06-01",
        "medication": "metformin",
        "diagnosis_years": 9,
        "hba1c": 8.2,
        "fasting_bg": 150.0,
        "activity": "low",
        "meal_pattern": "three_meals",
        "target_bg": 140,
    },
}

OUT_DIR = os.path.join(os.path.dirname(__file__), "demo_data")
os.makedirs(OUT_DIR, exist_ok=True)

for key, persona in PERSONAS.items():
    print(f"Simulating {key}...", flush=True)
    try:
        df = simulate(
            persona=persona,
            meal_events=MEALS,
            sim_hours=SIM_HOURS,
            cgm_seed=42,
            start_time=BASE_DATE,
        )
        # CGM 컬럼만 뽑아서 저장 (1분 해상도)
        cgm = df["CGM"].dropna()
        out_path = os.path.join(OUT_DIR, f"demo_{key}.csv")
        cgm.to_csv(out_path, header=False, index=False)
        print(f"  -> {out_path}  ({len(cgm)} rows, "
              f"min={cgm.min():.1f} max={cgm.max():.1f} mean={cgm.mean():.1f})")
    except Exception as e:
        print(f"  ERROR: {e}")

print("\nDone.")
