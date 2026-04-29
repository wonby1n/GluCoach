# 데이터 명세

> ⚠️ **시뮬레이터 출력이 진실의 원천**. 본 문서와 다르면 본 문서를 수정한다.
> ⚠️ **2026-04-29 개정**: 데이터는 meal-event 단위. 원본은 3-file CSV (users + meal_events + glucose_readings). 전처리 스크립트(`scripts/preprocess.py`) 이미 존재.

---

## 원본 데이터 (3-file)

위치: `simuldate_params/simulate/` (ai/ 외부, 같은 레포 루트)

```
simuldate_params/
├── PARAMETER_REFERENCE.md   ← 전체 설명
├── base/                    ← 시뮬레이터 ODE 파라미터 (ML X)
│   ├── t1d.csv      (62 파라미터, 30명)
│   ├── t2d.csv      (54 파라미터, 10명)
│   └── normal.csv   (38 파라미터, 11명)
└── simulate/                ← ML 학습용
    ├── users.csv               (108명, 19컬럼)
    ├── meal_events.csv         (~229건, 4컬럼)
    └── glucose_readings.csv    (~31,212건, 4컬럼; 5분 간격, 1일치/명)
```

### users.csv (108명, 19컬럼)

ML feature는 5개만 사용. 나머지는 시뮬레이션 시나리오 생성용 또는 informational.

| 컬럼 | 예시 | ML feature |
|---|---|---|
| source | `simulator` | ✗ |
| user_id | `sim_0001` | join key |
| diabetes_type | `T1D` / `T2D` / `Normal` | ✓ |
| birthdate | `1978-02-12` | ✗ |
| sex | `M` / `F` | ✗ |
| height_cm | `182.6` | ✗ |
| weight_kg | `82.9` | ✓ |
| treatment | `medication` | ✗ |
| medication_timing | `식후` | ✗ |
| medication | `메트포르민` | ✗ |
| diagnosis_years | `0.2` | ✗ |
| hba1c | (NaN) | ✗ |
| fasting_bg | `129.2` | ✓ |
| activity | `low` / `medium` / `high` | ✓ |
| meal_pattern | `regular_3`, `skip_breakfast`, ... (11가지) | ✓ |
| target_bg | `110.0` | ✗ |
| fbg_bin | `normal/prediabetes/diabetes/uncontrolled` | ✗ |
| weight_bin | `light/medium/heavy/very_heavy` | ✗ |
| category | `T1D_lo_diabetes_medium` | ✗ |

### meal_events.csv (4컬럼)

| 컬럼 | 예시 | 설명 |
|---|---|---|
| source | `simulator` | - |
| user_id | `sim_0001` | join key |
| time | `2026-04-27 08:46:11` | 식사 시각 |
| carbs | `49.8` | 탄수화물 g |

### glucose_readings.csv (4컬럼)

| 컬럼 | 예시 | 설명 |
|---|---|---|
| source | `simulator` | - |
| user_id | `sim_0001` | join key |
| time | `2026-04-27 00:00:00` | 5분 간격 측정 시각 |
| glucose | `123.85` | 혈당 mg/dL |

유저당 ~289 readings (1일 × 24시간 × 12회/시간).

### 세 파일 관계

```
users.csv         meal_events.csv      glucose_readings.csv
(유저 특성)        (언제 뭘 먹었나)       (혈당 시계열)
    │                   │                      │
    └───────────────────┴──────────────────────┘
                user_id + time 으로 join
                          ↓
            식사 시각 기준 전후 혈당 추출
                          ↓
              ML 학습용 레코드 1건
```

식사 "전" 은 시점 1개(current_glucose) 만 추출. 식사 "후" 는 +5min~+120min 24개 추출.

---

## Model 1 Canonical Schema (`{train,val,test}.csv`, 33 컬럼)

각 행 = **한 식사 이벤트**. 행 단위 독립 (sliding window 만들지 않음).

### 입력 features (9개)

| # | 컬럼 | 타입 | 값/단위 | 설명 |
|---|---|---|---|---|
| 1 | carbs | float64 | z-score | 식사 탄수화물 (정규화됨) |
| 2 | meal_time_sin | float64 | [-1, 1] | sin(2π × hour / 24) — **정규화 X** |
| 3 | meal_time_cos | float64 | [-1, 1] | cos(2π × hour / 24) — **정규화 X** |
| 4 | current_glucose | float64 | z-score | 식사 시점 BG mg/dL → 정규화 |
| 5 | fasting_bg | float64 | z-score | users.csv 의 공복기 BG → 정규화 |
| 6 | weight_kg | float64 | z-score | users.csv 의 체중 → 정규화 |
| 7 | activity | int64 | {0, 1, 2} | low=0, medium=1, high=2 |
| 8 | diabetes_type | int64 | {0, 1, 2} | T1D=0, T2D=1, Normal=2 |
| 9 | meal_pattern | int64 | {0..10} | 11가지 패턴 (아래 매핑) |

> meal_time 은 cyclic 변환 후 sin/cos 두 컬럼. [-1, 1] 범위로 이미 normalized 효과 있음. 추가 z-score 불필요.

### 출력 targets (24개)

| 컬럼 | 타입 | 단위 | 설명 |
|---|---|---|---|
| BG_5min | float64 | **z-score 정규화** | 식후 5분 BG (저장은 정규화된 값) |
| BG_10min | float64 | z-score 정규화 | 식후 10분 BG |
| ... | ... | ... | (5분 간격) |
| BG_120min | float64 | z-score 정규화 | 식후 120분 BG |

24개 시점, 5분 간격 = 식후 2시간 곡선.

> **24개 BG 모두 같은 분포(혈당 mg/dL)** 이므로 단일 StandardScaler 로 전체 fit (24개 컬럼 통합 통계). 추론 시 inverse_transform → raw mg/dL.

---

## 인코딩 매핑 (preprocess.py 와 동기)

```python
DIABETES_TYPE_MAP = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP = {"low": 0, "medium": 1, "high": 2}
MEAL_PATTERN_MAP = {
    "regular_3": 0,
    "skip_breakfast": 1,
    "skip_dinner": 2,
    "skip_lunch": 3,
    "skip_breakfast_dinner": 4,
    "skip_breakfast_lunch": 5,
    "skip_lunch_dinner": 6,
    "frequent_small": 7,
    "irregular": 8,
    "late_dinner": 9,
    "fasting_day": 10,
}
```

이 매핑은 추론 시점에서도 동일하게 적용해야 함 (API 라우터가 백엔드에서 받은 문자열을 같은 정수로 변환).

---

## Model 1 전처리 파이프라인 (preprocess.py)

`ai/scripts/preprocess.py` 기존 코드 기반. **수정 필요 사항** (P1-1 단계에서 처리):
1. `meal_time` → `meal_time_sin, meal_time_cos` 두 컬럼으로 분리 (cyclic 변환)
2. BG_5min ~ BG_120min 24개 컬럼도 z-score 정규화 (별도 BG scaler)
3. `scaler.pkl` 구조를 dict 로 변경 (아래 "Scaler 통합" 섹션)

### 단계
1. **로드**: 3개 CSV 읽기 + `time` 컬럼 datetime 변환
2. **glucose forward fill**: 유저별 5분 grid reindex + ffill (gap 메우기)
3. **레코드 생성** (각 meal_event 마다 1행):
   - 식사 시점 기준 ±2.5분 안의 가장 가까운 glucose → `current_glucose`
   - 식후 +5min~+120min (5분 간격, 24개) 의 가장 가까운 glucose → `BG_5min ~ BG_120min`
   - **120분치 모두 있어야 유효** (마지막 식사 등 짤리면 제외)
   - users.csv 에서 `diabetes_type, weight_kg, fasting_bg, activity, meal_pattern` 가져와 join
   - 식사 시각 → `hour = meal_time.hour + minute/60.0`
   - **cyclic 변환**: `meal_time_sin = sin(2π × hour / 24)`, `meal_time_cos = cos(...)`
4. **인코딩**: 문자열 카테고리 → 정수 (위 매핑)
5. **환자 단위 분할** (default seed=42):
   - 70% 환자 → train
   - 15% 환자 → val
   - 15% 환자 → test
   - **레코드 단위 X — 환자 단위. 같은 user_id 가 train/test 모두에 포함되지 않도록**
6. **정규화** (수정):
   - `MODEL1_FEATURE_SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"]` z-score (meal_time_sin/cos 제외)
   - `BG_TARGET_COLS = ["BG_5min", ..., "BG_120min"]` 별도 scaler 로 통합 z-score (24개 평탄화 후 단일 mean/std)
   - 카테고리(activity, diabetes_type, meal_pattern)는 정규화 X
   - **train fit, val/test transform** (data leakage 방지)
   - 두 scaler 를 dict 로 묶어 `ai/models/scaler.pkl` 에 저장 (아래 참고)

### 실행
```bash
cd ai/
python scripts/preprocess.py
# 또는
python scripts/preprocess.py --data-dir ../simuldate_params/simulate \
                             --output-dir data/processed \
                             --models-dir models
```

### 출력 컬럼 순서 (33개)
```
[carbs, meal_time_sin, meal_time_cos, current_glucose, fasting_bg, weight_kg,
 activity, diabetes_type, meal_pattern,
 BG_5min, BG_10min, ..., BG_120min]
```

---

## Model 2 시계열 데이터 명세

`ai/data/processed/timeseries/{train,val,test}.npz`

각 sample:
- `X_seq`: shape `[12]` — 최근 60분 BG (5분 간격), z-score 정규화
- `X_profile`: shape `[4]` — `[weight_kg_normalized, fasting_bg_normalized, activity_int, diabetes_type_int]`
- `y`: shape `[24]` — 향후 120분 BG (5분 간격), z-score 정규화 (BG scaler 동일)
- (메타) `user_id`: split 검증용

### 추출 방법 (prepare_timeseries.py — 신규 작성)

`glucose_readings.csv` + `meal_events.csv` 사용:

1. **유저별 5분 grid reindex + ffill** (preprocess.py 와 동일)
2. **식사 이벤트 시각 마킹**: 각 user_id 의 meal_events.time 위치 표시
3. **유효 윈도우 추출**: 시계열 위에서 sliding window
   - 윈도우 = 60분 input + 120분 output = 180분 (36 timestep)
   - **윈도우 안에 식사 이벤트가 있으면 제외** (식사 없는 시점 예측 학습용)
   - stride: 30분 (윈도우 간 6 timestep 간격) — 데이터 양 vs 다양성 trade-off
4. **환자 단위 분할**: Model 1 과 **동일한 user_id split** 사용
   - preprocess.py 출력에서 train/val/test 의 user_id 리스트 가져와서 적용
   - 두 모델이 같은 환자 셋으로 학습되어야 평가 일관성 유지
5. **정규화**:
   - `X_seq, y` 는 **Model 1 의 BG scaler 재사용** (시계열도 같은 BG 분포)
   - `X_profile` 의 weight_kg, fasting_bg 는 Model 1 의 feature scaler 재사용
6. **저장**: np.savez_compressed

### 예상 데이터 양

환자당 1일치 (288 timestep) + 식사 ~2건 가정:
- 식사가 차지하는 구간: 식사 ±90분 = 36 timestep × 2식 = 72 timestep
- 식사 없는 구간: ~216 timestep
- 윈도우 36 timestep, stride 6 → 환자당 ~30 윈도우
- 108명 → 약 3000~3500 sample

데이터 늘리면 (14일치 가정) 환자당 ~500 윈도우 × 108 = ~5만 sample.

---

## Scaler 통합 (`ai/models/scaler.pkl`)

dict 형태로 저장:

```python
import pickle, sklearn.preprocessing as skp

scaler = {
    "model1_features": skp.StandardScaler(),  # carbs, current_glucose, fasting_bg, weight_kg (4개)
    "bg_target": skp.StandardScaler(),         # BG_5min~BG_120min flatten 통합 (1-dim 통계)
    "profile": skp.StandardScaler(),           # weight_kg, fasting_bg (Model 2 profile, Model 1 과 공유 가능)
}

with open("ai/models/scaler.pkl", "wb") as f:
    pickle.dump(scaler, f)
```

추론 시:
- Model 1: `scaler["model1_features"].transform(X[:, [carbs, current_glucose, fasting_bg, weight_kg]])`
- Model 2: `scaler["bg_target"].transform(X_seq.reshape(-1,1)).flatten()` 식으로 시계열에 적용
- 출력 inverse: `scaler["bg_target"].inverse_transform(y_pred.reshape(-1,1)).flatten()`

---

## 추론 시 전처리 (서버 측)

### Model 1 (`POST /api/predict/glucose/meal`)

API 요청에서 받은 raw 값 → 학습 시 형식으로 변환:

1. 카테고리 문자열 → 정수 (위 매핑)
2. `hour = meal_time.hour + minute/60` → `meal_time_sin = sin(2π × hour / 24), meal_time_cos = cos(...)`
3. `current_glucose` = 백엔드가 넘긴 `recent_values` 의 가장 최근 값 (또는 직접 필드)
4. `fasting_bg, weight_kg, activity, diabetes_type, meal_pattern` 백엔드가 사용자 프로필에서 직접 전달
5. `scaler["model1_features"]` 로 transform (4개 컬럼)
6. 모델 forward → 정규화된 24개 BG → `scaler["bg_target"].inverse_transform(...)` → raw mg/dL 응답

### Model 2 (`POST /api/predict/glucose/now`)

1. `recent_values` (최근 60분 BG, 12개) → `scaler["bg_target"].transform(reshape(-1,1)).flatten()`
   - 클라이언트가 12개보다 적게 보내면 0번째 값으로 padding 또는 에러 (정책 결정 필요)
2. `weight_kg, fasting_bg` → `scaler["profile"].transform(...)` (또는 model1_features 와 공유)
3. `activity, diabetes_type` → 정수 변환
4. 모델 forward → 정규화된 24개 BG → inverse_transform → raw mg/dL 응답

---

## 검증 체크리스트

### Model 1 (`scripts/preprocess.py` 실행 후)
- [ ] `data/processed/{train,val,test}.csv` 모두 33 컬럼?
- [ ] 정규화 4개 컬럼 (carbs, current_glucose, fasting_bg, weight_kg) train 통계: 평균≈0, 표준편차≈1?
- [ ] meal_time_sin, meal_time_cos 가 [-1, 1] 범위?
- [ ] BG_*min 24개의 train 통계: 평균≈0, 표준편차≈1 (정규화 적용 확인)?
- [ ] 카테고리 컬럼이 모두 int64이고 값 범위가 가정과 일치?
- [ ] 환자 분리 검증: train/val/test 의 user_id 교집합 = 공집합?
- [ ] 결측치 0?

### Model 2 (`scripts/prepare_timeseries.py` 실행 후)
- [ ] `data/processed/timeseries/{train,val,test}.npz` 생성?
- [ ] 각 .npz 의 X_seq shape `[N, 12]`, X_profile `[N, 4]`, y `[N, 24]`?
- [ ] 윈도우 안에 식사 이벤트 없음 검증 (sample 일부 수동 검증)?
- [ ] Model 1 의 user_id split 과 동일한 환자가 같은 split에 들어갔는지?

답에 따라 본 문서 + `preprocess.py` / `prepare_timeseries.py` 갱신.

---

## 부록: Shanghai T1DM 데이터 (보류)

초기 계획엔 Shanghai T1DM 공개 데이터셋으로 sim-to-real 검증을 했으나, 현재 파이프라인은 시뮬레이터 단일 소스로 진행. Shanghai 통합이 필요해지면:

- 환자 1005 (T1D, CSII 펌프 사용자)의 .xls 시계열 → 식사 이벤트별 meal-event row로 추출
- diabetes_type=0 (T1D), activity=0 (정보 없음, 휴리스틱), meal_pattern=시간대 기반 추정
- 시뮬레이터 scaler.pkl 로 transform (Shanghai로 다시 fit X)
- 별도 split 추가 (`shanghai_eval/finetune/test`) 또는 personalization fine-tune 데이터로 사용

상세는 변경 시 본 섹션을 본문으로 승격.
