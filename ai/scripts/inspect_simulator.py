"""
시뮬레이터 출력 컬럼/형식 확인 스크립트.

목적: CLAUDE.md / DATA_SPEC.md의 가정과 실제 시뮬레이터 출력이 일치하는지 점검.

Usage:
    cd ai/
    python scripts/inspect_simulator.py
    python scripts/inspect_simulator.py --file data/glucose/simulator/some_file.csv
"""
import sys
import argparse
from pathlib import Path
import pandas as pd
import numpy as np


def inspect_file(path: Path) -> dict:
    """단일 시뮬레이터 파일 검사."""
    print(f"\n{'='*70}")
    print(f"  {path.name}")
    print(f"{'='*70}")
    
    df = pd.read_csv(path)
    
    info = {
        "filename": path.name,
        "rows": len(df),
        "columns": list(df.columns),
    }
    
    print(f"\n행 수: {len(df)}")
    print(f"컬럼 ({len(df.columns)}개):")
    for col in df.columns:
        dtype = df[col].dtype
        nan_pct = df[col].isna().mean() * 100
        sample = df[col].dropna().iloc[0] if df[col].dropna().shape[0] > 0 else "ALL NaN"
        print(f"  - {col:30s} | {str(dtype):15s} | NaN {nan_pct:5.1f}% | sample: {sample}")
    
    # ─── timestamp 추정 ───
    print(f"\n시간 컬럼 추정:")
    time_candidates = []
    for col in df.columns:
        if "time" in col.lower() or "date" in col.lower() or "stamp" in col.lower():
            time_candidates.append(col)
            try:
                ts = pd.to_datetime(df[col])
                duration = ts.max() - ts.min()
                intervals = ts.diff().dropna().dt.total_seconds() / 60
                median_interval = intervals.median()
                print(f"  - {col}: 기간 {duration}, 간격 약 {median_interval:.1f}분")
                info["time_column"] = col
                info["interval_min"] = median_interval
            except Exception as e:
                print(f"  - {col}: 파싱 실패 ({e})")
    
    if not time_candidates:
        print("  (시간 컬럼 없음)")
    
    # ─── glucose 추정 ───
    print(f"\n혈당 컬럼 추정:")
    bg_candidates = []
    for col in df.columns:
        if any(k in col.lower() for k in ["bg", "glucose", "cgm"]):
            bg_candidates.append(col)
            if pd.api.types.is_numeric_dtype(df[col]):
                vals = df[col].dropna()
                print(f"  - {col}: min {vals.min():.1f}, max {vals.max():.1f}, mean {vals.mean():.1f}")
                # 단위 추정
                if vals.mean() > 50:
                    print(f"    → 단위 추정: mg/dL (mean > 50)")
                else:
                    print(f"    → 단위 추정: mmol/L? (mean < 50, ×18로 mg/dL 변환 필요할 수 있음)")
                info["glucose_column"] = col
    
    if not bg_candidates:
        print("  (혈당 컬럼 없음)")
    
    # ─── 식사/인슐린 추정 ───
    print(f"\n식사/인슐린 컬럼 추정:")
    for col in df.columns:
        col_lower = col.lower()
        if any(k in col_lower for k in ["cho", "carb", "meal"]):
            print(f"  - {col} (식사 후보)")
        elif any(k in col_lower for k in ["insulin", "bolus", "basal", "pump"]):
            print(f"  - {col} (인슐린 후보)")
        elif any(k in col_lower for k in ["med", "drug", "agent"]):
            print(f"  - {col} (약물 후보)")
    
    # ─── 첫 5행 ───
    print(f"\n첫 5행:")
    print(df.head().to_string())
    
    return info


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", type=Path, default=None,
                        help="검사할 파일. 미지정 시 simulator 폴더 첫 파일")
    parser.add_argument("--all", action="store_true",
                        help="시뮬레이터 폴더 모든 파일 검사")
    args = parser.parse_args()
    
    if args.file:
        files_to_check = [args.file]
    else:
        sim_dir = Path("data/glucose/simulator")
        if not sim_dir.exists():
            print(f"\n[오류] {sim_dir} 폴더 없음.")
            print("CLAUDE.md 의 데이터 경로 확인 또는 폴더 생성.")
            return 1
        
        all_files = sorted(sim_dir.rglob("*.csv"))
        if not all_files:
            print(f"\n[오류] {sim_dir} 에 CSV 파일 없음.")
            return 1
        
        if args.all:
            files_to_check = all_files
        else:
            files_to_check = [all_files[0]]
            print(f"\n[안내] 첫 번째 파일만 검사: {all_files[0].name}")
            print(f"전체 검사하려면 --all 옵션 사용 (총 {len(all_files)}개)")
    
    infos = []
    for f in files_to_check:
        try:
            info = inspect_file(f)
            infos.append(info)
        except Exception as e:
            print(f"\n[오류] {f}: {e}")
    
    # ─── 종합 비교 ───
    if len(infos) > 1:
        print(f"\n\n{'='*70}")
        print("  파일 간 컬럼 일관성 검사")
        print(f"{'='*70}")
        first_cols = set(infos[0]["columns"])
        for info in infos[1:]:
            cols = set(info["columns"])
            if cols == first_cols:
                print(f"  ✓ {info['filename']}: 동일")
            else:
                print(f"  ! {info['filename']}: 차이")
                added = cols - first_cols
                removed = first_cols - cols
                if added:
                    print(f"    추가: {added}")
                if removed:
                    print(f"    누락: {removed}")
    
    # ─── DATA_SPEC.md 와 비교 ───
    print(f"\n\n{'='*70}")
    print("  CLAUDE.md / DATA_SPEC.md 와 비교")
    print(f"{'='*70}\n")
    
    if not infos:
        print("  검사된 파일 없음.")
        return 1
    
    info = infos[0]
    canonical = ["timestamp", "glucose", "carbs", "bolus", "basal", "oral_med"]
    actual_cols_lower = [c.lower() for c in info["columns"]]
    
    print("  Canonical 컬럼 매핑 후보:")
    for canon in canonical:
        # 가장 비슷한 실제 컬럼 찾기
        candidates = [c for c in info["columns"] if canon in c.lower() or c.lower() in canon]
        if not candidates:
            # 동의어 검색
            synonyms = {
                "timestamp": ["time", "date"],
                "glucose": ["bg", "cgm"],
                "carbs": ["cho", "meal"],
                "bolus": ["insulin", "pump"],
                "basal": ["basal"],
                "oral_med": ["med", "drug", "agent"],
            }
            for syn in synonyms.get(canon, []):
                candidates = [c for c in info["columns"] if syn in c.lower()]
                if candidates:
                    break
        
        status = "✓" if candidates else "?"
        print(f"    {status}  {canon:12s} ← {candidates if candidates else '(매칭 없음, 수동 확인 필요)'}")
    
    print("\n" + "=" * 70)
    print("  다음 단계")
    print("=" * 70)
    print("  1. 위 매핑이 맞는지 확인")
    print("  2. 안 맞으면 CLAUDE.md / DATA_SPEC.md 의 컬럼 매핑 섹션 업데이트")
    print("  3. 그 다음 P1-1 (prepare_data.py) 진행")
    print()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
