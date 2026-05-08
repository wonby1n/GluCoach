# 데이터 감사 보고서 (Step 0)

작성일: 2026-05-08  
목적: 2-Stage CGM 혈당 예측 모델 재설계를 위한 학습 데이터 현황 파악

---

## 1. 데이터셋 전체 구성

```
ai/data/
├── glucose_data/            (1.0 GB)
│   ├── glucose_sample_data.csv     # 샘플 시연용 (154행)
│   ├── normal_synth/               # 합성 정상인 데이터
│   │   ├── glucose_readings.csv    # 134,875 CGM 기록
│   │   ├── meal_events.csv         # 5,395 식사 이벤트
│   │   └── users.csv               # 200 사용자 프로필
│   └── plus_data/
│       ├── CGMND-af920dee.../      # CGMND 비당뇨 임상 데이터
│       └── HUPA-UCM Diabetes Dataset/  # T1D 환자 데이터 (28명)
├── New_sample/              (586 MB)  ← 한국 음식 이미지 라벨링 데이터 (모델 학습용 아님)
└── processed/               (178 MB)  ← 기존 ML용 전처리 완료 데이터
    ├── train.csv / val.csv / test.csv  # 146,129 행
    ├── mixed/, mixed_v2/, normal_synth/, shanghai/
    └── ...
```

> **주의**: Shanghai 원본 데이터는 `glucose_data/` 하위에 Excel 형식으로 존재  
> (`Shanghai_T1DM/`, `Shanghai_T2DM/` 폴더, 총 ~125개 파일)

---

## 2. 데이터셋별 상세 분석

---

### 2.1 normal_synth (simglucose 합성 데이터)

| 항목 | 내용 |
|------|------|
| 출처 | simglucose 기반 합성 생성 |
| 대상 | 정상인 200명 |
| 기록 수 | CGM 134,875행 / 식사 이벤트 5,395행 |
| CGM 간격 | 5분 |
| 식사 정보 | **탄수화물(g)만** 있음 |
| 사용자 정보 | weight_kg, diabetes_type("Normal"), activity, meal_pattern |

**glucose_readings.csv 컬럼:**
```
source, user_id, time, glucose
```

**meal_events.csv 컬럼:**
```
source, user_id, time, carbs
```

**users.csv 컬럼:**
```
user_id, diabetes_type, weight_kg, fasting_bg, activity, meal_pattern
```

**Stage 2 활용 가능 여부: ❌ 불가**  
탄수화물 이외 macro 정보 없음. 음식명 없음.  
→ **Stage 1 (Baseline) 학습 전용**

---

### 2.2 Shanghai 데이터셋 (T1DM + T2DM)

| 항목 | 내용 |
|------|------|
| 출처 | 중국 상하이 임상 연구 |
| 대상 | T1DM 환자 (~16명) + T2DM 환자 (~109명) |
| 원본 형식 | Excel (.xlsx), 환자별 파일 |
| CGM 단위 | mg/dL |
| 처리 완료 데이터 | processed/shanghai/ (train 2,179 / val 473 / test 413행) |

**원본 Excel 주요 컬럼:**
```
Date, CGM (mg/dl), Dietary intake
```

**`Dietary intake` 예시:**
```
"Steamed bun 100 g\nYogurt 50 g\nEgg 60 g"
"Rice 150 g\nVegetable 100 g\nMeat 80 g"
```

**Summary 파일 컬럼:**
```
Patient Number, Weight (kg), Fasting Plasma Glucose (mg/dl)
```

**현재 전처리 결과 (processed/shanghai/) 컬럼:**
```
user_id, carbs, meal_time_sin, meal_time_cos, current_glucose,
fasting_bg, weight_kg, activity, diabetes_type, meal_pattern,
BG_5min ~ BG_120min (24개)
```

> 현재 처리 파이프라인: `Dietary intake` 텍스트 → `food_carb_map.py`로 탄수화물만 추출  
> protein / fat / fiber / kcal 정보는 **아직 추출하지 않음**

**Stage 2 활용 가능 여부: ✅ 가능 (macro enrichment 후)**  
원본 `Dietary intake` 텍스트에서 음식명을 파싱하여 USDA FNDDS DB 또는 Claude API로 전체 macro 조회 가능.

---

### 2.3 CGMND (비당뇨 임상 연구)

| 항목 | 내용 |
|------|------|
| 출처 | 임상 연구 (비식별 처리됨) |
| 대상 | 비당뇨 소아/청소년 (~48명) |
| 원본 형식 | CSV, 18개 파일 |
| CGM 단위 | mg/dL |

**주요 파일 및 컬럼:**

| 파일 | 주요 컬럼 |
|------|----------|
| `NonDiabPtRoster.csv` | RecID, PtID, SiteID, PtStatus, AgeAsOfEnrollDt |
| `NonDiabDeviceCGM.csv` | PtID, DeviceDtDaysFromEnroll, DeviceTm, RecordType("CGM"/"Calibration"), Value |
| `NonDiabDeviceBGM.csv` | 혈당계 측정값 |
| `NonDiabParticipantLogs.csv` | 참가자 식사/활동 로그 (식사 데이터 가능성) |
| `NonDiabSampleResults.csv` | 검체 검사 결과 |
| `NonDiabBaselineComp.csv` | 베이스라인 측정값 |

> `NonDiabParticipantLogs.csv` 내용 추가 확인 필요 — 식사 기록 형식이 다를 수 있음

**Stage 2 활용 가능 여부: ⚠️ 조건부**  
`NonDiabParticipantLogs.csv`에 식사 기록이 있다면 활용 가능하나, 음식명/macro 형식 확인 필요.

---

### 2.4 HUPA-UCM (T1DM 환자 + 인슐린)

| 항목 | 내용 |
|------|------|
| 출처 | 스페인 HUPA-UCM 병원 임상 연구 |
| 대상 | T1DM 환자 28명 |
| 원본 형식 | CSV, 세미콜론(`;`) 구분 |
| CGM 단위 | **mmol/L** (mg/dL로 변환 필요: × 18.018) |

**컬럼:**
```
time, glucose, calories, heart_rate, steps, basal_rate,
bolus_volume_delivered, carb_input
```

**특이사항:**
- `carb_input` (g)만 있음, 음식명 없음
- 인슐린 투여 기록 포함 (basal_rate, bolus_volume_delivered)
- Fitbit 활동 데이터 연동 (calories, steps, heart_rate)
- 원본 데이터 5,697개 파일 (일별 Fitbit 로그 포함)

**Stage 2 활용 가능 여부: ❌ 불가**  
음식명 없음, 탄수화물만 있음. 인슐린 데이터가 혈당 반응에 영향을 주어 순수 음식 반응 학습 어려움.

---

### 2.5 New_sample (한국 음식 이미지 라벨링)

| 항목 | 내용 |
|------|------|
| 출처 | AI Hub 한국 음식 양 추정 데이터셋 |
| 내용 | 음식 이미지 + 분량 추정 라벨 |
| 형식 | 이미지(.jpg) + 텍스트 라벨 |

**현 작업과의 관련성: ❌ 무관**  
음식 시각화/인식 용도. 혈당 예측 모델 학습에는 직접 사용 불가.

---

### 2.6 processed/ (기존 전처리 완료 데이터)

| 항목 | 내용 |
|------|------|
| 총 행 수 | 146,129행 |
| split | train / val / test |
| 구성 | normal_synth + Shanghai 혼합 |

**공통 컬럼 (34개):**
```
user_id, carbs, meal_time_sin, meal_time_cos, current_glucose,
fasting_bg, weight_kg, activity, diabetes_type, meal_pattern,
BG_5min ~ BG_120min (24개)
```

> 모든 수치 컬럼이 z-score 정규화되어 있음  
> activity: 0=low, 1=medium, 2=high  
> diabetes_type: 0=T1D, 1=T2D, 2=Normal  
> meal_pattern: 0~10 (11가지 패턴)

**새 작업(Stage 2)에서의 활용: ⚠️ 제한적**  
protein/fat/fiber 없어 직접 사용 불가. 단, 전처리 파이프라인 참고용으로 활용.

---

## 3. 데이터 활용 매핑 (새 모델 아키텍처 기준)

| 데이터셋 | Stage 1 (Baseline) | Stage 2 (Meal Effect) |
|----------|--------------------|-----------------------|
| normal_synth | ✅ 주력 데이터 | ❌ 탄수화물만 있어 사용 불가 |
| Shanghai | ✅ 식사 간 구간 활용 | ✅ macro enrichment 후 사용 |
| CGMND | ✅ 식사 간 구간 활용 | ⚠️ 식사 로그 확인 후 결정 |
| HUPA-UCM | ⚠️ 인슐린 영향으로 오염 | ❌ 음식명 없음 |

---

## 4. Stage 2 데이터 가용량 추정

현재 파악 기준 Stage 2 학습 가능 식사 이벤트:

| 출처 | 식사 이벤트 수 | macro 상태 |
|------|---------------|-----------|
| Shanghai T1DM | ~16명 × 평균 식사 수 | 텍스트 → enrichment 필요 |
| Shanghai T2DM | ~109명 × 평균 식사 수 | 텍스트 → enrichment 필요 |
| CGMND | 확인 필요 | 확인 필요 |

> 실제 수는 Step 0 감사 스크립트 실행 후 정확히 파악 필요

---

## 5. 핵심 이슈 및 다음 액션

### 이슈 1: Shanghai macro enrichment 필요
`Dietary intake` 텍스트 파싱 → USDA FNDDS 조회 → Claude API fallback  
→ **Step 1 (Macro Enrichment 파이프라인)**으로 해결

### 이슈 2: CGMND 식사 기록 형식 미확인
`NonDiabParticipantLogs.csv` 내용 확인 필요  
→ Step 0 감사 스크립트에 포함

### 이슈 3: Stage 2 학습 데이터 절대량 부족 가능성
Shanghai 원본 환자 수 (~125명)가 많지 않음. XGBoost 사용 결정 타당 (소량 데이터에 적합)

### 다음 단계
1. Step 0 감사 스크립트 실행 → 정확한 식사 이벤트 수, 결측치 패턴 파악
2. CGMND 참가자 로그 내용 확인
3. Step 1 (Macro Enrichment 파이프라인) 설계

---

*이 보고서는 코드 실행 없이 파일 탐색 및 기존 스크립트 분석 기반으로 작성됨*  
*정확한 통계는 Step 0 감사 스크립트 실행 후 업데이트 예정*
