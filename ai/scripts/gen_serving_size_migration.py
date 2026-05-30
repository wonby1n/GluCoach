"""엑셀 식품중량 → V19 Flyway 마이그레이션 SQL 생성."""
import re
from pathlib import Path

import pandas as pd

EXCEL = r"C:\Users\SSAFY\Downloads\전국통합식품영양성분정보_음식_표준데이터-20260504.xls"
OUT = (
    Path(__file__).resolve().parent.parent.parent
    / "backend/src/main/resources/db/migration/V19__update_foods_serving_size.sql"
)


def parse_weight(val):
    if pd.isna(val):
        return None
    m = re.search(r"[\d.]+", str(val))
    if not m:
        return None
    v = float(m.group())
    return v if v > 0 else None


df = pd.read_excel(EXCEL, header=1)

rows = []
skipped = 0
for _, r in df.iterrows():
    code = str(r["식품코드"]).strip().replace("'", "''")
    w = parse_weight(r["식품중량"])
    if w is not None:
        rows.append((code, w))
    else:
        skipped += 1

value_lines = [f"  ('{code}', {w})" for code, w in rows]

sql_parts = [
    "-- V19: foods.serving_size 를 엑셀 식품중량 실측값으로 일괄 업데이트.",
    "-- 기존 100 고정값을 실제 제공 중량으로 교체.",
    "-- 영양소 컬럼(carbs_g 등)은 100g 기준 유지 -- 계산 시 serving_size/100 배율 적용.",
    "-- 단위(g/ml) 는 숫자만 저장, 단위 정보는 제거 (밀도 근사).",
    "-- 엑셀에 없는 food_api_id 는 변경 없음 (serving_size NULL 유지).",
    "",
    "UPDATE foods",
    "SET serving_size = v.weight,",
    "    updated_at   = CURRENT_TIMESTAMP",
    "FROM (VALUES",
    ",\n".join(value_lines),
    ") AS v(food_api_id, weight)",
    "WHERE foods.food_api_id = v.food_api_id;",
]

sql = "\n".join(sql_parts)
OUT.write_text(sql, encoding="utf-8")

print(f"완료: {len(rows)}개 row 생성, {skipped}개 스킵")
print(f"출력: {OUT}")
