"""
음식명 → 100g당 탄수화물(g) 매핑 테이블.

Shanghai T1DM 데이터셋의 식사 텍스트 처리용.
한국 음식이 아닌 중국식/서양식 위주.

사용:
    from food_carb_map import map_meal_to_carbs
    carbs_g = map_meal_to_carbs("Steamed bun 100 g")
"""
import re
import pandas as pd
from typing import Optional


# 100g (또는 100ml) 당 탄수화물 그램 (USDA, 식약처 영양정보 참고)
# 키는 소문자로 통일하여 매칭
FOOD_CARB_PER_100G = {
    # === 주식 / 곡물 ===
    "rice": 28.0,                          # 흰쌀밥
    "steamed rice": 28.0,
    "porridge": 12.0,                      # 죽
    "noodles": 25.0,                       # 면
    "bread": 49.0,                         # 식빵
    "steamed bun": 47.0,                   # 만두피류
    "steamed bread": 47.0,
    "steamed stuffed bun": 40.0,           # 속 있는 만두 (바오즈)
    "coarse grain": 40.0,                  # 잡곡
    "coarse grain steamed bread": 45.0,    # 잡곡빵
    "cereal": 65.0,                        # 시리얼
    "oatcake": 60.0,                       # 오트케이크
    "biscuit": 60.0,                       # 비스킷
    "burger": 30.0,                        # 햄버거 (전체)
    "deep-fried dough stick": 50.0,        # 유탸오 (꽈배기)
    "rice cake": 55.0,
    "dumpling": 25.0,                      # 만두/교자
    
    # === 단백질 ===
    "egg": 1.0,                            # 계란
    "meat": 0.0,                           # 고기 (탄수화물 거의 없음)
    "meat dish": 5.0,                      # 고기 요리 (양념 포함 가정)
    "chicken": 0.0,
    "pork": 0.0,
    "fish": 0.0,
    "beef": 0.0,
    "duck": 0.0,
    "tofu": 2.0,                           # 두부
    
    # === 채소 ===
    "vegetable": 5.0,                      # 채소 일반
    "vegetable dish": 7.0,                 # 채소 요리
    "boiled vegetable": 5.0,
    "cabbage": 5.0,
    "hangzhou cabbage": 4.0,
    "lettuce": 3.0,
    "broccoli": 7.0,
    "celery": 3.0,
    "carrot": 8.0,
    "tomato": 4.0,
    "cucumber": 3.0,
    "spinach": 4.0,
    "sweet potato leaves": 4.0,
    "green pepper": 5.0,
    "mushroom": 3.0,
    
    # === 과일 ===
    "apple": 14.0,
    "banana": 23.0,
    "watermelon": 8.0,
    "orange": 12.0,
    "pear": 15.0,
    "grape": 18.0,
    
    # === 유제품 ===
    "milk": 5.0,
    "yogurt": 5.0,
    "milk and coffee": 6.0,
    
    # === 음료 / 기타 ===
    "glucose": 100.0,                      # 포도당 용액
    "soup": 4.0,
    "mung bean soup": 12.0,                # 녹두탕
    "sugar": 100.0,
    
    # === 복합 요리 (대표 추정값) ===
    "shredded pork": 5.0,                  # 고기채 볶음
    "minced pork": 5.0,
    "braised pork": 5.0,
    "fried fish": 5.0,
    "sliced pork": 5.0,
    "steamed chicken": 3.0,
    "pork jerky": 20.0,                    # 육포 (조미 있음)
    
    # === Shanghai 데이터 추가 매핑 ===
    "crab": 0.0,
    "hairtail": 0.0,                       # 갈치
    "amaranth": 4.0,                       # 비름
    "amaranthus": 4.0,
    "radish": 4.0,
    "cashew nuts": 30.0,                   # 캐슈넛
    "plum": 11.0,
    "crust": 50.0,                         # 빵 껍질
    "roast mutton": 0.0,                   # 양고기
    "mutton": 0.0,
    "pancake": 50.0,
    "snacks": 60.0,                        # 과자류 평균
    "red tea": 0.0,                        # 홍차
    "tea": 0.0,
    "beef": 0.0,
    "beaf": 0.0,                           # 오타 처리
    "bean products": 8.0,                  # 콩 가공품
    "pumpkin": 7.0,
    "sandwich": 30.0,
    "roasted crucian carp": 0.0,           # 붕어 구이
}


# 카테고리 fallback (특정 음식이 매핑에 없을 때)
CATEGORY_FALLBACK = {
    "pork": 5.0, "chicken": 3.0, "beef": 5.0, "fish": 3.0, "duck": 3.0,
    "shredded": 5.0, "minced": 5.0, "braised": 5.0, "fried": 8.0, "sliced": 5.0,
    "steamed": 6.0, "boiled": 5.0,
    "vegetable": 5.0, "soup": 4.0, "salad": 5.0,
    "rice": 28.0, "noodle": 25.0, "bread": 47.0, "bun": 45.0,
    "fruit": 12.0, "milk": 5.0,
}


# 최후의 fallback (카테고리도 매칭 안 되면)
DEFAULT_CARB_PER_100G = 15.0  # 평균 추정치


def parse_food_item(text: str) -> Optional[tuple]:
    """
    "Steamed bun 100 g" → ("steamed bun", 100, "g")
    "Milk and coffee 200 ml" → ("milk and coffee", 200, "ml")
    "data not available" → None
    
    Returns:
        (food_name_lower, amount, unit) 또는 None
    """
    text = str(text).strip()
    
    if not text or "not available" in text.lower():
        return None
    
    # 숫자 + 단위 추출
    match = re.search(r"(.+?)\s*(\d+\.?\d*)\s*(g|ml|kg|l)\b", text, re.IGNORECASE)
    if match:
        name = match.group(1).strip().lower()
        amount = float(match.group(2))
        unit = match.group(3).lower()
        
        # ml은 g와 비슷하게 취급 (음료 밀도 ~1)
        # kg, l은 1000배
        if unit in ("kg", "l"):
            amount *= 1000
        
        return (name, amount, unit)
    
    # 숫자 없으면 이름만 추출
    name = text.strip().lower()
    return (name, 100.0, "g")  # 기본 100g 가정


def lookup_carb_per_100g(food_name: str) -> float:
    """음식명 → 100g당 탄수화물(g). 없으면 카테고리 fallback."""
    food_name = food_name.lower().strip()
    
    # 1. 정확 매칭
    if food_name in FOOD_CARB_PER_100G:
        return FOOD_CARB_PER_100G[food_name]
    
    # 2. 부분 매칭 (음식명에 키가 포함)
    for key, value in FOOD_CARB_PER_100G.items():
        if key in food_name or food_name in key:
            return value
    
    # 3. 카테고리 fallback
    for cat, value in CATEGORY_FALLBACK.items():
        if cat in food_name:
            return value
    
    # 4. 최후
    return DEFAULT_CARB_PER_100G


def map_meal_to_carbs(meal_text: str) -> float:
    """
    한 끼 식사 텍스트 → 총 탄수화물 그램.
    
    Args:
        meal_text: 줄바꿈으로 구분된 식사 항목들
                   예: "Steamed bun 100 g\nYogurt 50 g"
    
    Returns:
        총 탄수화물 그램 (float)
    """
    if not meal_text or pd.isna(meal_text):
        return 0.0
    
    total_carbs = 0.0
    items = str(meal_text).split("\n")
    
    for item in items:
        parsed = parse_food_item(item)
        if parsed is None:
            continue
        
        name, amount, unit = parsed
        carb_per_100g = lookup_carb_per_100g(name)
        carbs = carb_per_100g * (amount / 100.0)
        total_carbs += carbs
    
    return round(total_carbs, 1)


# 테스트
if __name__ == "__main__":
    import pandas as pd
    
    test_meals = [
        "Steamed bun 100 g\nYogurt 50 g",
        "Noodles 150 g",
        "Coarse grain 75 g\nVegetable 50 g\nMeat dish 50g",
        "Milk 100 g\nSteamed stuffed bun 50 g",
        "Apple 200 g",
        "data not available",
        "Burger 200 g",
    ]
    
    for meal in test_meals:
        carbs = map_meal_to_carbs(meal)
        print(f"  {carbs:>6.1f}g  | {meal[:60]}")
