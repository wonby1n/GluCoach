"""
Step 1: Macro Enrichment 파이프라인

Shanghai 식사 텍스트 → {carbs_g, protein_g, fat_g, fiber_g, kcal} 변환.
- 1차: USDA FoodData Central API 조회
- 2차: Claude API fallback (USDA 실패 시)
- 결과 캐시: data/food_macro_cache.json

실행:
    USDA_API_KEY=<key> python scripts/macro_lookup.py --build-cache
    USDA_API_KEY=<key> python scripts/macro_lookup.py --enrich-shanghai
"""

import os
import re
import sys
import json
import time
import argparse
import logging
from pathlib import Path
from typing import Optional

import requests
import pandas as pd
import numpy as np

# 기존 파서 재활용
sys.path.insert(0, str(Path(__file__).parent))
from food_carb_map import parse_food_item

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

# ── 경로 설정 ──────────────────────────────────────────────────────────────
ROOT = Path(__file__).parent.parent
DATA_ROOT = ROOT / "data"
SHANGHAI_ROOT = DATA_ROOT / "glucose_data" / "Shanghai"
CACHE_PATH = DATA_ROOT / "food_macro_cache.json"
OUTPUT_PATH = DATA_ROOT / "processed" / "shanghai_stage2_raw.csv"

# ── USDA API 설정 ──────────────────────────────────────────────────────────
USDA_BASE = "https://api.nal.usda.gov/fdc/v1"
USDA_API_KEY = os.environ.get("USDA_API_KEY", "")

# USDA 영양소 ID
NUTRIENT_IDS = {
    "protein_g":  1003,
    "fat_g":      1004,
    "carbs_g":    1005,
    "fiber_g":    1079,
    "kcal":       1008,
}

# 결과가 없을 때 반환할 기본값 (None = "조회 실패"로 표시)
EMPTY_MACRO = {k: None for k in NUTRIENT_IDS}


# ── USDA 조회 ──────────────────────────────────────────────────────────────

def query_usda(food_name: str, retries: int = 2) -> Optional[dict]:
    """
    음식명 → USDA 100g당 macro 반환.
    실패 시 None.
    """
    if not USDA_API_KEY:
        logger.warning("USDA_API_KEY not set, skipping USDA lookup")
        return None

    params = {
        "query": food_name,
        "api_key": USDA_API_KEY,
        "dataType": ["SR Legacy", "Foundation", "Survey (FNDDS)"],
        "pageSize": 3,
    }

    for attempt in range(retries):
        try:
            resp = requests.get(f"{USDA_BASE}/foods/search", params=params, timeout=10)
            resp.raise_for_status()
            data = resp.json()
        except Exception as e:
            logger.debug(f"USDA request failed (attempt {attempt+1}): {e}")
            time.sleep(1)
            continue

        foods = data.get("foods", [])
        if not foods:
            return None

        # 첫 번째 결과에서 영양소 추출
        food = foods[0]
        nutrients = {n["nutrientId"]: n.get("value", 0.0) for n in food.get("foodNutrients", [])}

        result = {}
        for key, nid in NUTRIENT_IDS.items():
            result[key] = float(nutrients.get(nid, 0.0))

        return result

    return None


# ── OpenAI fallback ────────────────────────────────────────────────────────

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
OPENAI_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
OPENAI_MODEL = "gpt-4o"

def query_openai_fallback(food_name: str) -> dict:
    """
    USDA 실패 시 OpenAI GPT-4o로 macro 추정.
    기존 앱의 OpenAI 설정 재활용.
    """
    if not OPENAI_API_KEY:
        logger.warning(f"OPENAI_API_KEY not set, returning zero for '{food_name}'")
        return _zero_macro()

    try:
        prompt = (
            f"Food item: {food_name}\n"
            "Give estimated nutritional values per 100g for this food. "
            "Reply ONLY with valid JSON, no explanation:\n"
            '{"carbs_g": <float>, "protein_g": <float>, "fat_g": <float>, "fiber_g": <float>, "kcal": <float>}'
        )
        resp = requests.post(
            f"{OPENAI_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {OPENAI_API_KEY}", "Content-Type": "application/json"},
            json={
                "model": OPENAI_MODEL,
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": 100,
                "temperature": 0,
            },
            timeout=15,
        )
        resp.raise_for_status()
        text = resp.json()["choices"][0]["message"]["content"].strip()
        match = re.search(r"\{.*\}", text, re.DOTALL)
        if match:
            macro = json.loads(match.group())
            return {k: float(macro.get(k, 0.0)) for k in NUTRIENT_IDS}
    except Exception as e:
        logger.debug(f"OpenAI fallback failed for '{food_name}': {e}")

    return _zero_macro()


def _zero_macro() -> dict:
    return {k: 0.0 for k in NUTRIENT_IDS}


# ── 캐시 ───────────────────────────────────────────────────────────────────

def load_cache() -> dict:
    if CACHE_PATH.exists():
        with open(CACHE_PATH, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_cache(cache: dict) -> None:
    CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False, indent=2)


def get_macro_per_100g(food_name: str, cache: dict) -> dict:
    """캐시 → USDA → OpenAI 순으로 조회."""
    key = food_name.lower().strip()
    if key in cache:
        return cache[key]

    result = query_usda(key)
    source = "usda"

    if result is None:
        result = query_openai_fallback(key)
        source = "openai"

    cache[key] = result
    logger.debug(f"[{source}] {food_name}: {result}")
    return result


def fill_missing_with_openai() -> None:
    """
    캐시에서 모든 macro가 0인 항목(USDA 실패)을 OpenAI로 재조회.
    --build-cache 완료 후 실행.
    """
    cache = load_cache()
    missing = [k for k, v in cache.items() if v and all(v.get(n, -1) == 0.0 for n in NUTRIENT_IDS)]
    logger.info(f"재조회 대상: {len(missing)}개")

    for i, food_name in enumerate(missing):
        result = query_openai_fallback(food_name)
        cache[food_name] = result
        if (i + 1) % 20 == 0:
            save_cache(cache)
            logger.info(f"  진행: {i+1}/{len(missing)}")
        time.sleep(0.1)

    save_cache(cache)
    logger.info("OpenAI 보완 완료")


# ── 한 끼 식사 텍스트 → macro 합산 ────────────────────────────────────────

def meal_text_to_macro(meal_text: str, cache: dict) -> dict:
    """
    "Rice 150 g\nVegetable 100 g\nBeef 15 g"
    → {carbs_g, protein_g, fat_g, fiber_g, kcal} (한 끼 합계)
    """
    totals = {k: 0.0 for k in NUTRIENT_IDS}
    items = str(meal_text).strip().split("\n")

    for item in items:
        parsed = parse_food_item(item.strip())
        if parsed is None:
            continue
        food_name, amount_g, _ = parsed
        per_100g = get_macro_per_100g(food_name, cache)
        if per_100g:
            for k in NUTRIENT_IDS:
                totals[k] += per_100g.get(k, 0.0) * (amount_g / 100.0)

    return {k: round(v, 2) for k, v in totals.items()}


# ── Shanghai 원본 Excel → enriched CSV ────────────────────────────────────

def enrich_shanghai(save_every: int = 50) -> pd.DataFrame:
    """
    Shanghai T1DM + T2DM 원본 Excel → macro enrichment → CSV.

    출력 컬럼:
        user_id, diabetes_type, meal_text,
        carbs_g, protein_g, fat_g, fiber_g, kcal,
        pre_meal_glucose, meal_time,
        BG_5min ~ BG_120min (24개)
    """
    cache = load_cache()
    records = []

    for dtype, folder in [("T1DM", "Shanghai_T1DM"), ("T2DM", "Shanghai_T2DM")]:
        folder_path = SHANGHAI_ROOT / folder
        files = sorted(
            [f for f in folder_path.iterdir()
             if f.suffix in (".xlsx", ".xls") and not f.name.startswith("~$")]
        )
        logger.info(f"{dtype}: {len(files)} files")

        for fpath in files:
            try:
                df = pd.read_excel(fpath)
            except Exception as e:
                logger.warning(f"  Skip {fpath.name}: {e}")
                continue

            user_id = fpath.name.split("_")[0]
            cgm_col = "CGM (mg / dl)"
            diet_col = "Dietary intake"

            if cgm_col not in df.columns or diet_col not in df.columns:
                continue

            df = df.reset_index(drop=True)
            meal_rows = df[diet_col].notna()

            for idx in df[meal_rows].index:
                meal_text = str(df.loc[idx, diet_col]).strip()

                # "data not available" 등 제외
                if not meal_text or meal_text.lower() in ("data not available", "未记录", "nan"):
                    continue

                pre_meal_glucose = df.loc[idx, cgm_col]
                if pd.isna(pre_meal_glucose):
                    continue

                # 식후 24 포인트 (5분 간격, 2시간)
                bg_labels = {}
                for step in range(1, 25):
                    label_key = f"BG_{step * 5}min"
                    future_idx = idx + step
                    if future_idx < len(df):
                        val = df.loc[future_idx, cgm_col]
                        bg_labels[label_key] = val if not pd.isna(val) else np.nan
                    else:
                        bg_labels[label_key] = np.nan

                # macro enrichment (캐시 우선)
                macro = meal_text_to_macro(meal_text, cache)

                meal_time = df.loc[idx, "Date"] if "Date" in df.columns else np.nan

                record = {
                    "user_id": user_id,
                    "diabetes_type": dtype,
                    "meal_text": meal_text,
                    "meal_time": meal_time,
                    "pre_meal_glucose": round(float(pre_meal_glucose), 2),
                    **macro,
                    **bg_labels,
                }
                records.append(record)

            # 주기적으로 캐시 저장
            if len(records) % save_every == 0 and records:
                save_cache(cache)
                logger.info(f"  캐시 저장: {len(cache)}건 / 식사 이벤트: {len(records)}건")

    save_cache(cache)
    result_df = pd.DataFrame(records)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    result_df.to_csv(OUTPUT_PATH, index=False)
    logger.info(f"저장 완료: {OUTPUT_PATH} ({len(result_df)}행)")
    return result_df


# ── 캐시 빌드 (음식명 목록만 미리 조회) ────────────────────────────────────

def build_cache_from_shanghai(max_workers: int = 8) -> None:
    """
    Shanghai 전체 음식명을 수집해서 USDA 병렬 조회 후 캐시 저장.
    """
    from concurrent.futures import ThreadPoolExecutor, as_completed
    import threading

    cache = load_cache()
    cache_lock = threading.Lock()
    food_names: set[str] = set()

    for folder in ["Shanghai_T1DM", "Shanghai_T2DM"]:
        folder_path = SHANGHAI_ROOT / folder
        files = sorted(
            [f for f in folder_path.iterdir()
             if f.suffix in (".xlsx", ".xls") and not f.name.startswith("~$")]
        )
        for fpath in files:
            try:
                df = pd.read_excel(fpath)
            except Exception:
                continue
            diet_col = "Dietary intake"
            if diet_col not in df.columns:
                continue
            for val in df[diet_col].dropna():
                for line in str(val).split("\n"):
                    parsed = parse_food_item(line.strip())
                    if parsed:
                        food_names.add(parsed[0].lower().strip())

    new_foods = [f for f in sorted(food_names) if f not in cache]
    logger.info(f"전체 고유 음식명: {len(food_names)}개 / 신규 조회 필요: {len(new_foods)}개")

    done = [0]

    def fetch_one(name: str) -> tuple[str, dict]:
        result = query_usda(name)
        if result is None:
            result = _zero_macro()  # OpenAI는 --fill-missing 단계에서
        return name, result

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = {executor.submit(fetch_one, name): name for name in new_foods}
        for future in as_completed(futures):
            name, result = future.result()
            with cache_lock:
                cache[name] = result
                done[0] += 1
                if done[0] % 50 == 0:
                    save_cache(cache)
                    logger.info(f"  진행: {done[0]}/{len(new_foods)}")

    save_cache(cache)
    logger.info(f"캐시 완성: {len(cache)}개 항목")


# ── 리포트 ─────────────────────────────────────────────────────────────────

def report_cache() -> None:
    cache = load_cache()
    total = len(cache)
    zero = sum(1 for v in cache.values() if v and v.get("carbs_g", -1) == 0.0 and v.get("protein_g", -1) == 0.0)
    logger.info(f"캐시 항목: {total}개")
    logger.info(f"  모든 macro가 0인 항목(추정 실패): {zero}개 ({zero/max(total,1)*100:.1f}%)")
    logger.info(f"  정상 항목: {total - zero}개")

    if OUTPUT_PATH.exists():
        df = pd.read_csv(OUTPUT_PATH)
        logger.info(f"\n최종 데이터셋: {OUTPUT_PATH}")
        logger.info(f"  행 수: {len(df)}")
        logger.info(f"  당뇨 타입별:\n{df['diabetes_type'].value_counts().to_string()}")
        logger.info(f"  macro 결측 비율:\n{df[list(NUTRIENT_IDS.keys())].isna().mean().mul(100).round(1).to_string()}")
        logger.info(f"\n  macro 평균:\n{df[list(NUTRIENT_IDS.keys())].mean().round(2).to_string()}")


# ── CLI ────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Shanghai macro enrichment")
    parser.add_argument("--build-cache", action="store_true", help="음식명 USDA 일괄 조회 후 캐시 저장")
    parser.add_argument("--fill-missing", action="store_true", help="USDA 실패 항목을 OpenAI로 재조회")
    parser.add_argument("--enrich-shanghai", action="store_true", help="Shanghai Excel → enriched CSV")
    parser.add_argument("--report", action="store_true", help="캐시 및 결과 현황 출력")
    parser.add_argument("--test", action="store_true", help="단일 음식 테스트")
    args = parser.parse_args()

    if args.build_cache:
        build_cache_from_shanghai()
    elif args.fill_missing:
        fill_missing_with_openai()
    elif args.enrich_shanghai:
        df = enrich_shanghai()
        print(df[["user_id", "diabetes_type", "carbs_g", "protein_g", "fat_g", "fiber_g", "kcal", "pre_meal_glucose"]].head(10).to_string())
    elif args.report:
        report_cache()
    elif args.test:
        cache = {}
        tests = [
            "Rice 150 g\nVegetable 100 g\nBeef 15 g",
            "Wonton 250 g",
            "Coarse grain steamed bread 136 g\nBoiled vegetable 103 g\nEgg 62 g",
            "Apple 70 g",
        ]
        for meal in tests:
            macro = meal_text_to_macro(meal, cache)
            print(f"\n{meal[:60]}")
            print(f"  → {macro}")
    else:
        parser.print_help()
