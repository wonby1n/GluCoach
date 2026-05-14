"""
전체 파이프라인 테스트.

AI 서버가 실행 중인 상태에서 실행하세요.

사용법:
  cd ai
  python scripts/test_pipeline.py              # 기본 (localhost:8000)
  python scripts/test_pipeline.py --url http://localhost:8000

체크 항목:
  1. AI 서버 health check
  2. 모델 파일 로드 확인
  3. 백엔드가 보내는 형식으로 요청 → 응답 구조 검증
  4. 응답값 범위 검증 (혈당 20~600 mg/dL, curve 24개 등)
  5. 개인화 없는 유저 / 다른 diabetes_type 케이스
  6. 필수 필드 누락 시 에러 처리 확인
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime

try:
    import requests
except ImportError:
    print("[오류] requests 패키지 필요: pip install requests")
    sys.exit(1)

BASE_URL = "http://localhost:8000"
MEAL_URL = "/inference/glucose/meal"
HEALTH_URL = "/inference/glucose/health"

PASS = "✅"
FAIL = "❌"
WARN = "⚠️"


# ─────────────────────────────────────────────────────────────────────
# 헬퍼
# ─────────────────────────────────────────────────────────────────────

def _post(url: str, payload: dict) -> tuple[int, dict]:
    try:
        r = requests.post(url, json=payload, timeout=15)
        try:
            body = r.json()
        except Exception:
            body = {"raw": r.text}
        return r.status_code, body
    except requests.exceptions.ConnectionError:
        print(f"\n{FAIL} AI 서버에 연결할 수 없습니다: {url}")
        print("   → AI 서버를 먼저 실행하세요:")
        print("     cd ai && uvicorn app.main:app --reload --port 8000")
        sys.exit(1)


def _check(label: str, condition: bool, detail: str = "") -> bool:
    icon = PASS if condition else FAIL
    msg = f"  {icon} {label}"
    if detail:
        msg += f"  ({detail})"
    print(msg)
    return condition


# ─────────────────────────────────────────────────────────────────────
# 테스트 케이스
# ─────────────────────────────────────────────────────────────────────

def make_request(
    user_id: str = "test_user_1",
    carbs: float = 66.0,
    protein_g: float = 8.0,
    fat_g: float = 6.0,
    fiber_g: float = 2.0,
    kcal: float = 350.0,
    pre_glucose: float = 110.0,
    diabetes_type: str = "T2D",
    food_name: str = "김밥",
) -> dict:
    """백엔드가 실제로 보내는 형식과 동일한 요청 생성."""
    return {
        "user_id": user_id,
        "recent_values": [pre_glucose],          # 백엔드 현재 구현: CGM 미연동, 단일값
        "meal": {
            "carbs": carbs,
            "time_iso": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
            "protein_g": protein_g,
            "fat_g": fat_g,
            "fiber_g": fiber_g,
            "kcal": kcal,
        },
        "user_profile": {
            "fasting_bg": 100.0,                 # 백엔드 하드코딩 기본값
            "weight_kg": 70.0,
            "activity": "medium",                # 백엔드 하드코딩 기본값
            "diabetes_type": diabetes_type,
            "meal_pattern": "regular_3",         # 백엔드 하드코딩 기본값
        },
    }


def validate_response(body: dict, label: str = "") -> bool:
    """AI 응답이 백엔드가 기대하는 구조인지 검증."""
    prefix = f"[{label}] " if label else ""
    ok = True

    # 필수 필드 존재
    ok &= _check(f"{prefix}curve 필드 존재", "curve" in body)
    ok &= _check(f"{prefix}peak_mgdl 필드 존재", "peak_mgdl" in body)
    ok &= _check(f"{prefix}peak_minute 필드 존재", "peak_minute" in body)
    ok &= _check(f"{prefix}model_type 필드 존재", "model_type" in body)
    ok &= _check(f"{prefix}confidence 필드 존재", "confidence" in body)

    if "curve" not in body:
        return False

    curve = body["curve"]
    ok &= _check(f"{prefix}curve 24개 시점", len(curve) == 24, f"실제: {len(curve)}개")

    # curve 각 원소 구조
    if curve:
        first = curve[0]
        ok &= _check(f"{prefix}curve[0].minute_offset 존재", "minute_offset" in first)
        ok &= _check(f"{prefix}curve[0].glucose_mgdl 존재", "glucose_mgdl" in first)

        # 시점 순서: 5, 10, 15, ... 120
        offsets = [p["minute_offset"] for p in curve]
        ok &= _check(
            f"{prefix}curve 시점 순서 (5~120, 5분 간격)",
            offsets == list(range(5, 125, 5)),
            f"실제: {offsets[:4]}...",
        )

        # 혈당 값 범위
        glucose_vals = [p["glucose_mgdl"] for p in curve]
        in_range = all(20 <= v <= 600 for v in glucose_vals)
        ok &= _check(
            f"{prefix}혈당 범위 20~600 mg/dL",
            in_range,
            f"min={min(glucose_vals):.1f}, max={max(glucose_vals):.1f}",
        )

    # peak 검증
    if "peak_mgdl" in body and "curve" in body and curve:
        glucose_vals = [p["glucose_mgdl"] for p in curve]
        actual_peak = max(glucose_vals)
        reported_peak = body["peak_mgdl"]
        ok &= _check(
            f"{prefix}peak_mgdl 값 일치",
            abs(actual_peak - reported_peak) < 0.1,
            f"curve max={actual_peak:.1f}, peak_mgdl={reported_peak:.1f}",
        )

    if "peak_minute" in body:
        ok &= _check(
            f"{prefix}peak_minute 범위 (5~120)",
            5 <= body["peak_minute"] <= 120,
            f"실제: {body['peak_minute']}분",
        )

    if "confidence" in body:
        ok &= _check(
            f"{prefix}confidence 범위 (0~1)",
            0 <= body["confidence"] <= 1,
            f"실제: {body['confidence']}",
        )

    if "model_type" in body:
        ok &= _check(
            f"{prefix}model_type 값",
            body["model_type"] in ("base", "personalized", "stage2"),
            f"실제: {body['model_type']}",
        )

    return ok


# ─────────────────────────────────────────────────────────────────────
# 테스트 실행
# ─────────────────────────────────────────────────────────────────────

def test_health(base_url: str) -> bool:
    print("\n" + "=" * 60)
    print("1. Health Check")
    print("=" * 60)
    try:
        r = requests.get(f"{base_url}{HEALTH_URL}", timeout=5)
        body = r.json()
    except requests.exceptions.ConnectionError:
        print(f"{FAIL} 서버 연결 실패. AI 서버를 실행하세요.")
        return False

    _check("서버 응답", r.status_code == 200, f"HTTP {r.status_code}")
    _check("식사 모델 로드", body.get("meal_model_loaded", False))
    _check("현재시점 모델 로드", body.get("now_model_loaded", False))
    _check("scaler 로드", body.get("scaler_loaded", False))

    status = body.get("status", "UNKNOWN")
    icon = PASS if status == "UP" else (WARN if status == "DEGRADED" else FAIL)
    print(f"  {icon} 서버 상태: {status}")

    if body.get("cuda_available"):
        print(f"  {PASS} GPU 사용 가능")
    else:
        print(f"  {WARN} GPU 없음 (CPU 모드)")

    print(f"\n  상세: {json.dumps(body, ensure_ascii=False)}")
    return status in ("UP", "DEGRADED")


def test_basic_request(base_url: str) -> bool:
    print("\n" + "=" * 60)
    print("2. 기본 요청 (김밥, T2D, 식전혈당 110)")
    print("=" * 60)

    payload = make_request(food_name="김밥", carbs=66, protein_g=8, fat_g=6, fiber_g=2,
                           pre_glucose=110, diabetes_type="T2D")
    print(f"\n  요청:\n{json.dumps(payload, ensure_ascii=False, indent=4)}")

    status, body = _post(f"{base_url}{MEAL_URL}", payload)
    _check("HTTP 200", status == 200, f"실제: {status}")

    if status != 200:
        print(f"  오류 응답: {body}")
        return False

    print(f"\n  응답 요약:")
    print(f"    model_type  : {body.get('model_type')}")
    print(f"    confidence  : {body.get('confidence')}")
    print(f"    peak_mgdl   : {body.get('peak_mgdl')} mg/dL")
    print(f"    peak_minute : {body.get('peak_minute')} 분")

    if "curve" in body:
        vals = [p["glucose_mgdl"] for p in body["curve"]]
        print(f"    혈당 곡선   : {[round(v) for v in vals]}")

    return validate_response(body, "기본")


def test_high_carb_vs_low_carb(base_url: str) -> bool:
    print("\n" + "=" * 60)
    print("3. 탄수화물 민감도 — 고탄수(흰쌀밥) vs 저탄수(두부)")
    print("=" * 60)

    cases = [
        ("흰쌀밥 (탄수 75g)", make_request(carbs=75, protein_g=5, fat_g=1, fiber_g=0.5,
                                            pre_glucose=110, food_name="흰쌀밥")),
        ("두부 (탄수 3g)", make_request(carbs=3, protein_g=8, fat_g=5, fiber_g=0.5,
                                         pre_glucose=110, food_name="두부")),
    ]

    peaks = []
    for name, payload in cases:
        status, body = _post(f"{base_url}{MEAL_URL}", payload)
        if status == 200 and "peak_mgdl" in body:
            peak = body["peak_mgdl"]
            peaks.append(peak)
            print(f"  {name:25s} → 피크: {peak:.1f} mg/dL @ {body.get('peak_minute')}분")
        else:
            print(f"  {FAIL} {name} 요청 실패: {status}")
            return False

    if len(peaks) == 2:
        diff = peaks[0] - peaks[1]
        ok = diff > 5
        _check(
            f"고탄수 > 저탄수 피크",
            ok,
            f"차이: {diff:.1f} mg/dL {'(정상)' if ok else '(탄수화물 민감도 낮음)'}",
        )
        return ok
    return False


def test_diabetes_types(base_url: str) -> bool:
    print("\n" + "=" * 60)
    print("4. diabetes_type 값 검증 (T1D / T2D / Normal)")
    print("=" * 60)

    ok = True
    for dtype in ["T1D", "T2D", "Normal"]:
        payload = make_request(diabetes_type=dtype, carbs=60)
        status, body = _post(f"{base_url}{MEAL_URL}", payload)
        _check(
            f"diabetes_type={dtype} 응답 정상",
            status == 200,
            f"HTTP {status}",
        )
        if status != 200:
            print(f"    오류: {body}")
            ok = False
    return ok


def test_error_handling(base_url: str) -> bool:
    print("\n" + "=" * 60)
    print("5. 에러 처리 — 잘못된 입력")
    print("=" * 60)

    ok = True

    # recent_values 없음 — Pydantic min_length=1 → 422
    payload = make_request()
    payload["recent_values"] = []
    status, body = _post(f"{base_url}{MEAL_URL}", payload)
    _check("recent_values=[] → 422", status == 422, f"실제: {status}")
    ok &= status == 422

    # 잘못된 diabetes_type
    payload = make_request()
    payload["user_profile"]["diabetes_type"] = "INVALID"
    status, body = _post(f"{base_url}{MEAL_URL}", payload)
    _check("diabetes_type 잘못됨 → 422", status == 422, f"실제: {status}")
    ok &= status == 422

    return ok


def print_db_check_guide():
    print("\n" + "=" * 60)
    print("6. DB 저장 확인 (수동)")
    print("=" * 60)
    print("""
  백엔드 + AI 서버 둘 다 실행 중일 때:

  [방법 1] 앱에서 직접 음식 선택 → 예측 요청 발생

  [방법 2] 백엔드 API 직접 호출:
    POST http://localhost:8080/api/predict/glucose
    Authorization: Bearer {토큰}
    {
      "foodName": "김밥",
      "carbsG": 66,
      "proteinG": 8,
      "fatG": 6,
      "fiberG": 2,
      "kcal": 350
    }

  [DB 확인 쿼리]:
    SELECT id, user_id, food_name, predicted_peak, created_at
    FROM glucose_predictions
    ORDER BY created_at DESC
    LIMIT 5;

  [기대 결과]:
    - glucose_predictions 테이블에 새 행 추가
    - predicted_curve 컬럼에 JSON 배열 (24개 시점)
    - predicted_peak에 피크 혈당값
""")


# ─────────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8000", help="AI 서버 URL")
    args = parser.parse_args()
    base_url = args.url.rstrip("/")

    print("=" * 60)
    print(f"  AI 서버 파이프라인 테스트")
    print(f"  대상: {base_url}")
    print("=" * 60)

    results = {}
    results["health"]       = test_health(base_url)
    results["basic"]        = test_basic_request(base_url)
    results["carb_sens"]    = test_high_carb_vs_low_carb(base_url)
    results["dtype"]        = test_diabetes_types(base_url)
    results["error"]        = test_error_handling(base_url)
    print_db_check_guide()

    print("\n" + "=" * 60)
    print("  최종 결과")
    print("=" * 60)
    all_pass = True
    for name, ok in results.items():
        icon = PASS if ok else FAIL
        print(f"  {icon}  {name}")
        if not ok:
            all_pass = False

    print()
    if all_pass:
        print(f"  {PASS} 전체 통과")
    else:
        print(f"  {FAIL} 일부 실패 — 위 항목 확인 필요")
    print()


if __name__ == "__main__":
    main()
