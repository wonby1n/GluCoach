"""
Step 0: Data Audit Script
Shanghai + normal_synth 데이터 현황 파악.
Stage 2 (XGBoost meal effect) 학습 가능 이벤트 수, 음식 텍스트 품질, 결측치 패턴을 점검.
"""

import os
import glob
import sys
import pandas as pd
import numpy as np
from collections import Counter

sys.stdout.reconfigure(encoding="utf-8")

DATA_ROOT = os.path.join(os.path.dirname(__file__), "..", "data")
SHANGHAI_ROOT = os.path.join(DATA_ROOT, "glucose_data", "Shanghai")
NORMAL_SYNTH_ROOT = os.path.join(DATA_ROOT, "glucose_data", "normal_synth")

CGM_COL = "CGM (mg / dl)"
DIET_COL = "Dietary intake"
INSULIN_BOLUS_CSII = "CSII - bolus insulin (Novolin R, IU)"
INSULIN_SC = "Insulin dose - s.c."

# 식후 2시간 곡선 추출에 필요한 최소 CGM 포인트 수 (5분 간격 × 24 = 2시간)
MIN_POSTMEAL_POINTS = 20  # 24 중 20개 이상이면 "깔끔"으로 판정


def load_excel_safe(path: str) -> pd.DataFrame | None:
    try:
        return pd.read_excel(path)
    except Exception as e:
        print(f"  [ERROR] {os.path.basename(path)}: {e}")
        return None


def is_valid_diet_text(text) -> bool:
    """NaN, 빈 문자열, 'data not available' 제외."""
    if pd.isna(text):
        return False
    s = str(text).strip().lower()
    return s not in ("", "data not available", "未记录", "na", "n/a")


def has_insulin(row: pd.Series) -> bool:
    def _positive(val) -> bool:
        try:
            return not pd.isna(val) and float(val) > 0
        except (ValueError, TypeError):
            return False

    return _positive(row.get(INSULIN_BOLUS_CSII)) or _positive(row.get(INSULIN_SC))


def check_postmeal_curve(df: pd.DataFrame, meal_idx: int) -> bool:
    """식사 이벤트 이후 2시간(24 포인트) CGM이 충분한지 확인."""
    end_idx = meal_idx + 24
    if end_idx > len(df):
        return False
    window = df.iloc[meal_idx : end_idx][CGM_COL]
    valid = window.notna().sum()
    return valid >= MIN_POSTMEAL_POINTS


def audit_shanghai(folder: str, dtype: str) -> dict:
    """T1DM 또는 T2DM 폴더 전체를 감사."""
    files = glob.glob(os.path.join(folder, "*.xlsx")) + glob.glob(
        os.path.join(folder, "*.xls")
    )
    files = [f for f in files if not os.path.basename(f).startswith("~$")]

    patient_ids = set()
    total_cgm_rows = 0
    total_meal_events = 0
    valid_diet_events = 0
    clean_curve_events = 0
    insulin_at_meal = 0
    diet_texts: list[str] = []
    diet_counter: Counter = Counter()

    for fpath in sorted(files):
        df = load_excel_safe(fpath)
        if df is None:
            continue

        pid = os.path.basename(fpath).split("_")[0]
        patient_ids.add(pid)

        if CGM_COL not in df.columns:
            continue

        # CGM을 5분 그리드로 재정렬 (원본 그대로 인덱스 사용)
        df = df.reset_index(drop=True)
        total_cgm_rows += df[CGM_COL].notna().sum()

        meal_rows = df[DIET_COL].notna() if DIET_COL in df.columns else pd.Series(False, index=df.index)
        for idx in df[meal_rows].index:
            total_meal_events += 1
            diet_text = df.loc[idx, DIET_COL]

            if is_valid_diet_text(diet_text):
                valid_diet_events += 1
                diet_texts.append(str(diet_text).strip())
                # 음식 개별 항목 카운트 (줄바꿈으로 구분)
                for item in str(diet_text).split("\n"):
                    food_name = item.strip().split(" ")[0].lower()
                    if food_name:
                        diet_counter[food_name] += 1

            if has_insulin(df.loc[idx]):
                insulin_at_meal += 1

            if check_postmeal_curve(df, idx):
                clean_curve_events += 1

    return {
        "dtype": dtype,
        "n_files": len(files),
        "n_patients": len(patient_ids),
        "total_cgm_rows": total_cgm_rows,
        "total_meal_events": total_meal_events,
        "valid_diet_events": valid_diet_events,
        "clean_curve_events": clean_curve_events,
        "insulin_at_meal": insulin_at_meal,
        "diet_texts_sample": diet_texts[:5],
        "top_foods": diet_counter.most_common(15),
    }


def audit_normal_synth() -> dict:
    cgm_path = os.path.join(NORMAL_SYNTH_ROOT, "glucose_readings.csv")
    meal_path = os.path.join(NORMAL_SYNTH_ROOT, "meal_events.csv")
    user_path = os.path.join(NORMAL_SYNTH_ROOT, "users.csv")

    cgm_df = pd.read_csv(cgm_path)
    meal_df = pd.read_csv(meal_path)
    user_df = pd.read_csv(user_path)

    return {
        "n_users": len(user_df),
        "cgm_rows": len(cgm_df),
        "meal_events": len(meal_df),
        "carbs_mean": meal_df["carbs"].mean(),
        "carbs_std": meal_df["carbs"].std(),
        "carbs_min": meal_df["carbs"].min(),
        "carbs_max": meal_df["carbs"].max(),
        "cgm_missing_pct": cgm_df["glucose"].isna().mean() * 100,
        "diabetes_types": user_df["diabetes_type"].value_counts().to_dict(),
        "activity_dist": user_df["activity"].value_counts().to_dict(),
    }


def print_separator(title: str):
    print("\n" + "=" * 60)
    print(f"  {title}")
    print("=" * 60)


def main():
    print_separator("normal_synth (Stage 1 데이터)")
    ns = audit_normal_synth()
    print(f"  사용자 수       : {ns['n_users']}")
    print(f"  CGM 기록 수     : {ns['cgm_rows']:,}")
    print(f"  식사 이벤트 수  : {ns['meal_events']:,}")
    print(f"  탄수화물 g      : mean={ns['carbs_mean']:.1f}, std={ns['carbs_std']:.1f}, "
          f"min={ns['carbs_min']:.1f}, max={ns['carbs_max']:.1f}")
    print(f"  CGM 결측치      : {ns['cgm_missing_pct']:.2f}%")
    print(f"  당뇨 타입       : {ns['diabetes_types']}")
    print(f"  활동 분포       : {ns['activity_dist']}")

    print_separator("Shanghai T1DM (Stage 2 후보)")
    t1 = audit_shanghai(os.path.join(SHANGHAI_ROOT, "Shanghai_T1DM"), "T1DM")
    print(f"  파일 수         : {t1['n_files']}")
    print(f"  환자 수         : {t1['n_patients']}")
    print(f"  CGM 기록 수     : {t1['total_cgm_rows']:,}")
    print(f"  전체 식사 이벤트: {t1['total_meal_events']}")
    print(f"  유효 음식 텍스트: {t1['valid_diet_events']} "
          f"({t1['valid_diet_events']/max(t1['total_meal_events'],1)*100:.1f}%)")
    print(f"  깔끔한 2h 곡선  : {t1['clean_curve_events']} "
          f"({t1['clean_curve_events']/max(t1['total_meal_events'],1)*100:.1f}%)")
    print(f"  식사 시 인슐린  : {t1['insulin_at_meal']} "
          f"({t1['insulin_at_meal']/max(t1['total_meal_events'],1)*100:.1f}%) ← Stage 2 오염 위험")
    print("  음식 텍스트 샘플:")
    for s in t1["diet_texts_sample"]:
        print(f"    └ {repr(s[:80])}")
    print("  상위 음식 항목:")
    for food, cnt in t1["top_foods"]:
        print(f"    {food:<25} {cnt:>4}회")

    print_separator("Shanghai T2DM (Stage 2 후보)")
    t2 = audit_shanghai(os.path.join(SHANGHAI_ROOT, "Shanghai_T2DM"), "T2DM")
    print(f"  파일 수         : {t2['n_files']}")
    print(f"  환자 수         : {t2['n_patients']}")
    print(f"  CGM 기록 수     : {t2['total_cgm_rows']:,}")
    print(f"  전체 식사 이벤트: {t2['total_meal_events']}")
    print(f"  유효 음식 텍스트: {t2['valid_diet_events']} "
          f"({t2['valid_diet_events']/max(t2['total_meal_events'],1)*100:.1f}%)")
    print(f"  깔끔한 2h 곡선  : {t2['clean_curve_events']} "
          f"({t2['clean_curve_events']/max(t2['total_meal_events'],1)*100:.1f}%)")
    print(f"  식사 시 인슐린  : {t2['insulin_at_meal']} "
          f"({t2['insulin_at_meal']/max(t2['total_meal_events'],1)*100:.1f}%) ← Stage 2 오염 위험")
    print("  음식 텍스트 샘플:")
    for s in t2["diet_texts_sample"]:
        print(f"    └ {repr(s[:80])}")
    print("  상위 음식 항목:")
    for food, cnt in t2["top_foods"]:
        print(f"    {food:<25} {cnt:>4}회")

    print_separator("종합 요약")
    stage2_candidates = t1["valid_diet_events"] + t2["valid_diet_events"]
    stage2_clean = t1["clean_curve_events"] + t2["clean_curve_events"]
    print(f"  [Stage 1] normal_synth 식사 이벤트  : {ns['meal_events']:,}건")
    print(f"  [Stage 2] 유효 음식 텍스트 합계      : {stage2_candidates}건")
    print(f"  [Stage 2] 깔끔한 2h 곡선 합계        : {stage2_clean}건")
    print(f"  [주의] T1DM 인슐린 오염 이벤트       : {t1['insulin_at_meal']}건 → 제외 권장")
    print()


if __name__ == "__main__":
    main()
