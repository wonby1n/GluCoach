# Stage 2 XGBoost 식사 혈당 예측 — 음식 비교 기능 개발 보고서

작성일: 2026-05-08

---

## 1. 배경 및 문제 정의

### 왜 기존 LSTM 모델로는 안 되는가

기존 Model 1(식사 기반 LSTM)은 RMSE@30 기준 10.21 mg/dL로 우수한 수치를 냈지만, **음식 비교 기능에는 치명적인 결함**이 있었다.

**평균 회귀(Mean Regression) 문제:**  
LSTM이 입력 분포의 평균에 가까운 곡선만 출력함. 즉, 김밥(carbs 66g)이든 닭가슴살(carbs 0g)이든 거의 동일한 혈당 곡선을 예측했다.

```
LSTM 기존 예측:
  김밥 66g carbs  → peak +45 mg/dL @ 40min
  닭가슴살 0g carbs → peak +43 mg/dL @ 38min  ← 구분 불가
```

음식 A/B 비교 기능의 핵심은 **어떤 음식이 혈당을 더 올리는가** 를 구별하는 것이다. 이 기능이 없으면 서비스 의미가 없다.

**근본 원인:**
- 탄수화물 단일 피처로는 음식 간 혈당 반응 차이를 포착하기 어려움
- 단백질·지방·식이섬유의 흡수 속도 조절 효과가 모델에 반영되지 않음
- LSTM은 시계열 패턴에 강하지만 음식 조성 차이에는 약함

---

## 2. 아키텍처 재설계: 2-Stage 구조

### Stage 1 (Baseline) — 단순화

| | 기존 계획 | 실제 채택 | 이유 |
|---|---|---|---|
| 방식 | LSTM으로 현재 혈당 궤적 예측 | `pre_meal_glucose` 단일 값 사용 | CGM 미구현으로 `recent_values = [100.0]` 하드코딩 상태. baseline 예측 모델 추가 의미 없음 |

### Stage 2 (Meal Effect) — 핵심

XGBoost가 음식 macro → **4개 스칼라** 예측:

| 스칼라 | 의미 |
|--------|------|
| `peak_delta` | 식전 혈당 대비 최대 상승량 (mg/dL) |
| `time_to_peak` | 피크 도달까지 걸리는 시간 (분) |
| `decay_rate` | 피크 이후 혈당 회복 속도 |
| `iauc` | 2시간 혈당-시간 적분 (식후 혈당 부담 지표) |

4개 스칼라 → **Skewed Gaussian**으로 24-point 곡선 재구성:
```
상승 구간: sigma_rise = ttp / 2.5        (빠른 상승)
하강 구간: sigma_fall = 120 / (decay+1)  (느린 회복)
```

---

## 3. 데이터 파이프라인

### 3-1. 데이터 소스 선택

| 데이터셋 | 특징 | Stage 2 적합성 |
|----------|------|----------------|
| sim_v3 (시뮬레이터) | 35,640명, 대용량 | 음식명/macro 없음 → 사용 불가 |
| Shanghai 임상 | T1DM 6명 + T2DM 101명 | 음식명 있음, BG 곡선 있음 → 사용 |
| normal_synth | Claude가 생성한 가상 데이터 | 신뢰도 우려 → 제외 결정 |

최종 선택: **Shanghai 임상 데이터** (실제 환자 데이터)

### 3-2. 문제: macro 정보 없음

Shanghai 원본 데이터에는 음식명과 탄수화물만 있고 단백질·지방·식이섬유 없음. Stage 2에는 전체 macro가 필수.

**해결: USDA FoodData Central API + GPT-4o 폴백**

```
Shanghai 음식명 (중국어/한자)
  ↓
USDA FoodData Central API
  [Nutrient IDs: protein=1003, fat=1004, carbs=1005, fiber=1079, kcal=1008]
  ThreadPoolExecutor(max_workers=8) 병렬 처리
  ↓ (API 결과 없거나 모두 0인 경우)
GPT-4o 폴백
  [gms.ssafy.io 프록시 경유]
  ↓
food_macro_cache.json (1,988개 항목 캐시)
  ↓
shanghai_stage2.csv
```

**문제 발생 및 해결 과정:**

| 문제 | 원인 | 해결 |
|------|------|------|
| 캐시 구축 속도 극히 느림 | USDA API 순차 호출 (1,988건 × 0.15s) | `ThreadPoolExecutor(max_workers=8)` 병렬화 → 5~6배 속도 향상 |
| ANTHROPIC_API_KEY 없음 | 폴백으로 Claude 사용 시도 | OpenAI GPT-4o로 전환 (프로젝트 기존 API 재사용) |
| USDA kcal 오류 | nutrient ID 1008이 일부 식품에서 잘못된 값 반환 (평균 187 kcal, 실제 ~345 kcal) | **Atwater 공식**으로 재계산: `kcal = carbs×4 + protein×4 + fat×9` |
| all-zero macro 행 (19.4%) | 한자 복합 요리명을 API가 인식 못함 | 해당 655행 필터링 제거 |
| `np.trapz` AttributeError | NumPy 2.0에서 제거됨 | `np.trapezoid`로 교체 |
| `has_insulin` TypeError | 값이 문자열일 때 `sc > 0` 비교 실패 | `try/float()` 래핑 |

### 3-3. 최종 데이터셋 (`data/processed/shanghai_stage2.csv`)

| 항목 | 수치 |
|------|------|
| 행 수 | 2,716건 (원본 3,371 → 655 필터링) |
| 당뇨 유형 | T2DM 2,354건 / T1DM 362건 |
| 컬럼 | user_id, diabetes_type, meal_time, pre_meal_glucose, carbs_g, protein_g, fat_g, fiber_g, kcal, BG_5min ~ BG_120min (24개) |

---

## 4. XGBoost 학습

### 특징 공학

| 피처 | 설명 |
|------|------|
| `carbs_g`, `protein_g`, `fat_g`, `fiber_g`, `kcal` | macro 영양소 |
| `pre_meal_glucose` | 식전 혈당 |
| `hour_sin`, `hour_cos` | 식사 시간대 Circadian 인코딩 |
| `diabetes_type` | 당뇨 유형 (Normal=0, T1D=1, T2D=2) |
| `carb_ratio` | 탄수화물 / (탄수+단백+지방) 비율 |
| `protein_fat` | 단백질+지방 합계 |

**총 11개 피처**, 사용자 기준 train/val/test 분리 (data leakage 방지)

### 학습 설정 (XGB_PARAMS)

```python
max_depth=5, n_estimators=500, learning_rate=0.05,
subsample=0.8, colsample_bytree=0.8,
reg_alpha=0.1, reg_lambda=1.0,
early_stopping_rounds=50, random_state=42
```

### 학습 결과

| 타겟 | MAE | RMSE |
|------|-----|------|
| peak_delta (mg/dL) | 36.27 | - |
| time_to_peak (분) | - | - |
| decay_rate | - | - |
| iauc | - | - |

| 지표 | peak_delta | iauc |
|------|-----------|------|
| Pairwise Ranking Accuracy | 0.600 | - |
| Spearman r | 0.285 | - |

---

## 5. 주요 문제: 방향성 오류

### 증상

```
XGBoost 단독 예측:
  닭가슴살 (carbs 0g)  → peak +55 mg/dL  ← 더 높음 (잘못됨)
  김밥 (carbs 66g)     → peak +48 mg/dL
```

탄수화물이 0g인 닭가슴살이 66g인 김밥보다 혈당 스파이크가 크게 예측되는 방향성 오류.

### 원인 분석

Shanghai 데이터가 **당뇨 환자 임상 데이터**이기 때문에 발생:
- 고탄수 식사를 할 때 인슐린 주사 → 혈당 상승 억제
- 결과적으로 학습 데이터에서 `carbs↑ → peak_delta↓` 역(逆)상관 패턴 존재
- 식사 시간대(`hour`) 피처가 탄수화물보다 강한 예측 신호로 학습됨

### 해결: Linear Prior Blending (Zeevi et al., Cell 2015)

```
최종값 = XGBoost × α + LinearPrior × (1 - α)
```

**Linear Prior 계수** (실제 생리학적 근거):

```python
# peak_delta: carbs ↑ → 혈당 ↑, fiber/fat/protein → 완화
linear_peak = 10.0 + carbs×0.80 - fiber×1.50 - fat×0.30 - protein×0.20

# time_to_peak: fat/fiber → 위 배출 지연 → peak 늦춤
linear_ttp  = 35.0 + fat×0.50 + fiber×0.30 + protein×0.20 - carbs×0.10
```

**블렌딩 가중치:**

| 타겟 | α (XGBoost 비중) | 1-α (Linear Prior 비중) | 이유 |
|------|-----------------|------------------------|------|
| peak_delta | 0.35 | 0.65 | 방향성 보장이 최우선 |
| time_to_peak | 0.40 | 0.60 | 중간 블렌딩 |
| decay_rate | 0.60 | 0.40 | XGBoost 신뢰 (방향성 무관) |

### Sanity Check 결과 (pre_meal_glucose=100 mg/dL 기준)

| 음식 | carbs | peak_delta | time_to_peak | 예측 peak 혈당 |
|------|-------|-----------|-------------|--------------|
| 김밥 | 66g | +60.2 mg/dL | 40.5분 | **160.2 mg/dL** |
| 국밥 | 40g | +43.7 mg/dL | 47.9분 | **143.6 mg/dL** |
| 닭가슴살 | 0g | +28.4 mg/dL | 45.5분 | **128.4 mg/dL** |

→ 김밥 > 국밥 > 닭가슴살 ✓ **올바른 순서**

---

## 6. 식사 혈당 예측 흐름 (POST /inference/glucose/meal)

```
[프론트엔드 (Android)]
  POST /api/predict/glucose  (Spring Boot)
    │  user_id, foodId (또는 carbsG 수기 입력)
    ▼
[Spring Boot Backend]
  PredictionService.buildAiRequest()
    │  Food 엔티티에서 carbs 조회 (foodId 있을 때)
    │  user_profile 기본값 채움 (fasting_bg=100, activity=medium 등)
    ▼
  POST /inference/glucose/meal  →  [AI FastAPI Server :8000]
    │
    │  요청 JSON:
    │  {
    │    "user_id": "1",
    │    "recent_values": [100.0],          ← CGM 미구현, 하드코딩
    │    "meal": {
    │      "carbs": 66.0,
    │      "time_iso": "2026-05-08T12:00:00",
    │      "protein_g": null,               ← 현재 미전송 (개선 필요)
    │      "fat_g": null,
    │      "fiber_g": null
    │    },
    │    "user_profile": { ... }
    │  }
    ▼
[interface.py: predict_meal_response()]

  ┌─ meal.protein_g is not None? ──────────────────────────────────┐
  │                                                                  │
  YES (macro 있음)                                     NO (carbs만) │
  │                                                                  │
  ▼                                                                  ▼
[Stage 2 경로]                                          [Legacy LSTM 경로]
  Stage2Predictor.predict()                               LSTM/MLP/Ridge 모델
    XGBoost 4개 모델 추론                                  → 실패 시 dummy
    + Linear Prior 블렌딩
    → {peak_delta, time_to_peak, decay_rate, iauc}
  build_curve()
    Skewed Gaussian → 24-point curve
  model_type = "stage2"
  confidence = 0.85

    ▼ (공통)
  PredictResponse {
    curve: [{minute_offset: 5, glucose_mgdl: 103.2}, ...],  ← 24개 시점
    peak_mgdl: 160.2,
    peak_minute: 40,
    model_type: "stage2",
    confidence: 0.85
  }
    ▼
[Spring Boot]  GlucosePrediction DB 저장 → 응답
    ▼
[프론트엔드]  두 음식에 대해 2회 호출 → 곡선 비교 시각화
```

---

## 7. 파일 구조

```
ai/
├── models/
│   └── stage2_meal/
│       ├── peak_delta.pkl      XGBoost — 혈당 최대 상승량 예측
│       ├── time_to_peak.pkl    XGBoost — 피크 도달 시간 예측
│       ├── decay_rate.pkl      XGBoost — 회복 속도 예측
│       ├── iauc.pkl            XGBoost — 2시간 혈당 부담 예측
│       └── meta.json           feature_names, target_names
├── data/
│   ├── food_macro_cache.json   음식명 → macro 캐시 (1,988개)
│   └── processed/
│       └── shanghai_stage2.csv 학습 데이터 (2,716건)
├── scripts/
│   ├── step0_data_audit.py     데이터셋 감사
│   ├── macro_lookup.py         USDA + GPT-4o macro 보강 파이프라인
│   └── train_stage2.py         XGBoost Stage 2 학습 스크립트
└── app/
    └── glucose/
        ├── stage2_model.py     Stage2Predictor + Linear Prior 블렌딩
        ├── curve_builder.py    Skewed Gaussian 곡선 재구성
        └── interface.py        Stage 2 / Legacy 경로 라우팅
```

---

## 8. 현황 및 남은 과제

### 완료

- [x] Shanghai 데이터 audit 및 macro 보강 파이프라인 구축
- [x] USDA API + GPT-4o 폴백 + JSON 캐시 (1,988 항목)
- [x] Stage 2 XGBoost 4-target 모델 학습 및 저장
- [x] Linear Prior Blending으로 방향성 오류 해결
- [x] Skewed Gaussian curve_builder 구현
- [x] interface.py에 Stage 2 / Legacy 경로 라우팅 구현
- [x] Sanity check: 김밥 > 국밥 > 닭가슴살 순서 확인

### 미완료 (다음 작업)

- [ ] **백엔드 MealInfo.java에 macro 필드 추가** — 현재 protein_g/fat_g/fiber_g 미전송 → Stage 2 미트리거
  - `MealInfo.java`: proteinG, fatG, fiberG, kcal 필드 추가
  - `PredictionService.java`: `buildAiRequest()`에서 Food 엔티티 macro 전달
- [ ] Stage 2 재학습 — T2DM 데이터만 사용 + 더 강한 feature engineering (현재 ranking_acc 0.600)
- [ ] 통합 테스트: Spring Boot ↔ AI 서버 실제 요청 흐름 검증

### 알려진 한계

| 항목 | 현황 | 개선 방향 |
|------|------|----------|
| `recent_values` | [100.0] 하드코딩 (CGM 미구현) | CGM 연동 후 실측값 사용 |
| ranking_acc | 0.600 (이상적: 0.75+) | T2DM 단독 재학습, 개인화 fine-tune |
| 학습 데이터 | 2,716건 (소규모) | 실사용자 데이터 누적 후 재학습 |
| 인슐린 confounding | T1DM 데이터에서 약물 영향 | 인슐린 용량 피처 추가 또는 T1DM 별도 모델 |
