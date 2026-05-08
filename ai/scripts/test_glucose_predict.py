"""
interface.predict_meal_response() test.
Stage 2 path (with macros) / Legacy path (carbs only) / Sanity check.
"""
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.glucose.interface import predict_meal_response

BASE_REQUEST = {
    "user_id": "test-user",
    "recent_values": [100.0],
    "user_profile": {
        "fasting_bg": 100.0,
        "weight_kg": 65.0,
        "activity": "medium",
        "diabetes_type": "T2D",
        "meal_pattern": "regular_3",
    },
}


def make_request(food_name, carbs, protein=None, fat=None, fiber=None, pre_glucose=100.0):
    req = {**BASE_REQUEST, "recent_values": [pre_glucose]}
    req["meal"] = {
        "carbs": carbs,
        "time_iso": "2026-05-08T12:00:00",
        "protein_g": protein,
        "fat_g": fat,
        "fiber_g": fiber,
    }
    return food_name, req


def print_result(food_name, resp):
    curve_values = [p["glucose_mgdl"] for p in resp["curve"]]
    print(f"  음식       : {food_name}")
    print(f"  model_type : {resp['model_type']}")
    print(f"  confidence : {resp['confidence']}")
    print(f"  peak_mgdl  : {resp['peak_mgdl']} mg/dL")
    print(f"  peak_minute: {resp['peak_minute']} 분")
    print(f"  curve (5~30분): {curve_values[:6]}")
    print(f"  curve (60~90분): {curve_values[11:15]}")
    print()


def run():
    print("=" * 60)
    print("  혈당 예측 모델 테스트")
    print("=" * 60)

    # Case 1: Stage 2 path (macro included)
    print("\n[Case 1] Stage 2 경로 -- macro 있음 (protein_g 제공)")
    print("-" * 60)
    cases = [
        make_request("김밥",    carbs=66, protein=8,  fat=6,  fiber=2),
        make_request("국밥",    carbs=40, protein=20, fat=15, fiber=1),
        make_request("닭가슴살", carbs=0,  protein=30, fat=3,  fiber=0),
    ]
    results = []
    for food_name, req in cases:
        resp = predict_meal_response(req).model_dump()
        print_result(food_name, resp)
        results.append((food_name, resp["peak_mgdl"]))

    print("  [순서 검증] 고탄수 > 저탄수 순서가 맞아야 함")
    for i, (name, peak) in enumerate(results):
        print(f"    {i+1}. {name}: {peak} mg/dL")
    ordered = all(results[i][1] >= results[i+1][1] for i in range(len(results)-1))
    print(f"  결과: {'OK - 올바른 순서' if ordered else 'FAIL - 순서 오류'}")

    # Case 2: Legacy path (carbs only)
    print("\n[Case 2] Legacy 경로 -- macro 없음 (carbs만)")
    print("-" * 60)
    _, req = make_request("밥 (carbs만)", carbs=60)
    resp = predict_meal_response(req).model_dump()
    print_result("밥 (carbs만)", resp)

    # Case 3: Same food, different pre-meal glucose
    print("[Case 3] 같은 음식, 다른 식전 혈당")
    print("-" * 60)
    for pre_gl, label in [(80.0, "저혈당  80"), (100.0, "정상   100"), (140.0, "고혈당 140")]:
        _, req = make_request("김밥", carbs=66, protein=8, fat=6, fiber=2, pre_glucose=pre_gl)
        resp = predict_meal_response(req).model_dump()
        print(f"  식전 {label} mg/dL -> peak: {resp['peak_mgdl']} mg/dL @ {resp['peak_minute']}분")
    print()

    # Case 4: Edge case - 0g carbs
    print("[Case 4] 탄수화물 0g 극단 케이스 (물)")
    print("-" * 60)
    _, req = make_request("물", carbs=0, protein=0, fat=0, fiber=0)
    resp = predict_meal_response(req).model_dump()
    print_result("물 (carbs=0, macro=0)", resp)

    print("=" * 60)
    print("  테스트 완료")
    print("=" * 60)


if __name__ == "__main__":
    run()
