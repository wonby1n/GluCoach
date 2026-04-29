# AI 모듈 - 혈당 예측 시스템

> **본 문서는 ai/ 폴더 기준이며, 모든 경로는 ai/를 루트로 함.**

---

## ⚠️ 작업 원칙 (가장 중요)

### 원칙 1: 시뮬레이터 데이터가 진실의 원천(source of truth)
- **시뮬레이터(simglucose 보강 버전) 출력 형식이 표준**
- 원본 데이터: `simuldate_params/simulate/{users,meal_events,glucose_readings}.csv` (3개 파일)
- 전처리 결과: `ai/data/processed/{train,val,test}.csv` (32 컬럼 meal-event)
- **실제 시뮬레이터 출력과 다르면 시뮬레이터 형식에 맞춰 본 문서를 수정**

### 원칙 2: 모든 결정은 유동적
- 컬럼명, 폴더 구조, 전처리 로직, 모델 입력 차원 — 모두 변경 가능
- 변경 시 영향 받는 파일들도 함께 수정
- "이 문서에 이렇게 써있어서" 라는 이유로 비합리적 선택 금지

### 원칙 3: 작업 시작 전 환경 검증 필수
1. `python scripts/check_env.py` 실행 (GPU, 데이터, .env 등)
2. `python scripts/preprocess.py` 실행 (3-file → meal-event flat 변환, scaler 저장)
3. `processed/{train,val,test}.csv` 의 컬럼/분포가 본 문서와 일치하는지 확인
4. 다르면 본 문서와 DATA_SPEC.md 수정 후 다음 단계

### 원칙 4: 결정 사항 기록
변경 시 본 문서 하단의 "변경 이력" 섹션에 한 줄 추가.

---

## 프로젝트 개요

SSAFY 캡스톤. 화이바이오메드 비침습 CGM 패치를 위한 AI 백엔드.
패치는 임상 전 단계로 인체 데이터 없음. 따라서 자체 시뮬레이터로 가상 환자(T1D/T2D/정상) 데이터 생성하여 학습.

본 폴더(ai/)는 자체 FastAPI 서버 (`ai/app/main.py`).
- **추론 코드**: `ai/app/glucose/` — 모델 학습/추론/평가 함수
- **REST API** (Jira 278): `ai/app/api/glucose.py`
  - `POST /api/predict/glucose/meal` — Model 1, 음식 선택 시점에서 식후 120분 예측
  - `POST /api/predict/glucose/now` — Model 2, 현재 시점에서 향후 120분 예측 (식사 없음 가정)
- **백엔드 협의 결과** (Step 0):
  - 사용자 파라미터(`fasting_bg, weight_kg, activity, diabetes_type`)는 백엔드가 본 ai 서버에 함께 넘김
  - 응답: **24개 BG 시퀀스** (5분 간격, 곡선 시각화용)
- 음식 사진 인식은 같은 ai/app/ 내 다른 모듈(다른 팀원 담당). 본 모듈과 독립.

---

## 핵심 전략: 두 모델 분리 학습

| | Model 1 (식사 시점) | Model 2 (현재 시점) |
|---|---|---|
| 호출 시점 | 음식 선택 시 | 그 외 (식사 없는 상태) |
| 입력 | 9-dim feature (8 + meal_time sin/cos) | 최근 60분 BG 시계열 (12 timestep) + user_profile |
| 출력 | 식후 120분 BG (24개, 5분 간격) | 향후 120분 BG (24개, 5분 간격) |
| 데이터 | `processed/{train,val,test}.csv` (meal-event flat) | `processed/timeseries/{train,val,test}.npz` (sliding window) |
| 모델 | Ridge / MLP / LSTM(encoder-decoder) | LSTM time-series |
| 엔드포인트 | `/api/predict/glucose/meal` | `/api/predict/glucose/now` |

**환자 단위 70/15/15 분할** — 두 모델 모두 같은 환자 split 유지 (모델 1과 2의 train 환자가 같은 풀, val/test 도 동일 환자 셋). 같은 환자 데이터가 train+test에 동시 포함되지 않도록.

(옵션) 개인화: 환자당 식사 이벤트 적으면 효과 제한적, 시간 남으면 진행.

> **Shanghai T1DM 통합은 보류**. 현재 시뮬레이터 단일 소스로 진행. 발표에서 임상 안전성 주장하려면 추후 합류 검토.

---

## 환자 타입 인코딩

본 시뮬레이터는 약물(bolus/basal/oral_med)을 분리해서 출력하지 **않음**.
대신 `diabetes_type` 카테고리로 환자 정보를 단일 feature로 압축:

| 원본 문자열 | 인코딩 값 | 모델 내부 처리 |
|---|---|---|
| `T1D` | 0 | 시뮬레이터가 인슐린 곡선 영향을 이미 반영해서 BG 출력 |
| `T2D` | 1 | 시뮬레이터가 경구약 영향을 이미 반영해서 BG 출력 |
| `Normal` | 2 | 약물 영향 없음 |

다른 카테고리 인코딩 (preprocess.py 와 동기):

| 컬럼 | 매핑 |
|---|---|
| `activity` | `low=0, medium=1, high=2` |
| `meal_pattern` | `regular_3=0, skip_breakfast=1, skip_dinner=2, skip_lunch=3, skip_breakfast_dinner=4, skip_breakfast_lunch=5, skip_lunch_dinner=6, frequent_small=7, irregular=8, late_dinner=9, fasting_day=10` (총 11가지) |

> 약물의 양/타이밍은 시뮬레이터 내부에서 처리됨. 모델은 **diabetes_type 카테고리**만 봄.

---

## 모델 사양 (변경 가능)

### Model 1: 식사 시점 → 식후 120분 BG

- **입력**: 9-dim feature (시계열 아님, 식사 시점 스냅샷)
  - 연속형 (z-score 정규화됨, 6개): `carbs`, `current_glucose`, `fasting_bg`, `weight_kg`, `meal_time_sin`, `meal_time_cos`
    - meal_time 은 **cyclic 변환**: `sin(2π × hour / 24)`, `cos(2π × hour / 24)` 두 컬럼으로 분리 (23시-1시 가까움 표현)
  - 카테고리 (3개): `activity` (0/1/2), `diabetes_type` (0/1/2), `meal_pattern` (0~10)
- **출력**: 24-dim 식후 BG 곡선
  → `BG_5min, BG_10min, ..., BG_120min` (5분 간격, 식후 2시간)
  → **z-score 정규화 학습, 추론 시 inverse_transform → raw mg/dL**
- **모델 후보 (3-way 비교)**:
  - Ridge multi-output regression (sklearn) — 베이스라인
  - MLP (9 → 64 → 64 → 24, dropout 0.2) — 단순 회귀
  - LSTM encoder-decoder — 출력 시퀀스의 시간적 일관성 유지 (Jira 명시)
- 카테고리 feature는 임베딩(MLP/LSTM) 또는 one-hot(Ridge) 처리

### Model 2: 현재 시점 → 향후 120분 BG (식사 없음 가정)

- **입력**: 시계열 BG + 사용자 profile
  - BG 시계열 12 timestep × 1 channel: 최근 60분 BG (5분 간격), z-score 정규화
  - user_profile (4개 정수/연속): `weight_kg, fasting_bg, activity, diabetes_type` — meal_pattern 은 식사 안 하는 컨텍스트라 제외
- **출력**: 24-dim 향후 BG 곡선 (5분 간격, 5min~120min)
  → z-score 정규화, 추론 시 inverse
- **모델**: LSTM time-series (단순 encoder LSTM → Linear 24)
- **학습 데이터**: `glucose_readings.csv` 에서 sliding window 추출. 식사 이벤트가 윈도우(60+120=180분) 안에 없는 구간만

---

## 데이터 (canonical schema)

### 원본 (3-file)
```
simuldate_params/
├── PARAMETER_REFERENCE.md
├── base/                ← 시뮬레이터 ODE 파라미터 (ML 학습 X)
│   ├── t1d.csv, t2d.csv, normal.csv
└── simulate/            ← ML 학습용
    ├── users.csv               (108명, 19컬럼)
    ├── meal_events.csv         (~229건, 4컬럼: source/user_id/time/carbs)
    └── glucose_readings.csv    (~31,212건, 4컬럼: source/user_id/time/glucose)
```

`users.csv` 19컬럼 중 ML feature로 쓰는 건 **5개**: `diabetes_type, weight_kg, fasting_bg, activity, meal_pattern`. 나머지(birthdate, sex, height_cm, treatment, medication, hba1c, target_bg, fbg_bin, weight_bin, category 등)는 informational.

### Model 1 전처리 결과 (`ai/data/processed/{train,val,test}.csv`, 33 컬럼)

각 행 = **한 식사 이벤트**. 행 단위 독립적.

| # | 컬럼 | 타입 | 단위/값 | 설명 |
|---|---|---|---|---|
| 1 | carbs | float64 | z-score | 탄수화물 g |
| 2 | meal_time_sin | float64 | z-score (또는 raw [-1,1]) | sin(2π × hour / 24) |
| 3 | meal_time_cos | float64 | z-score (또는 raw [-1,1]) | cos(2π × hour / 24) |
| 4 | current_glucose | float64 | z-score | 식사 시점 BG mg/dL |
| 5 | fasting_bg | float64 | z-score | users.csv 공복기 BG |
| 6 | weight_kg | float64 | z-score | users.csv 체중 |
| 7 | activity | int64 | {0,1,2} | low/medium/high |
| 8 | diabetes_type | int64 | {0,1,2} | T1D=0, T2D=1, Normal=2 |
| 9 | meal_pattern | int64 | {0..10} | 11가지 식사 패턴 |
| 10~33 | BG_5min ~ BG_120min | float64 | **z-score 정규화됨** | 식후 24개 시점 BG (학습 시 정규화, 추론 시 inverse) |

> meal_time_sin/cos 는 이미 [-1, 1] 범위라 z-score 적용해도 효과 미미. **raw [-1,1] 그대로 두기 권장** (scaler 적용 안 함).

### Model 2 전처리 결과 (`ai/data/processed/timeseries/{train,val,test}.npz`)

NumPy 압축 파일. CSV가 아닌 이유: 시계열은 행 가변 길이 + 다차원이라 .npz 가 효율적.

각 sample:
- `X_seq`: shape `[12]` — 최근 60분 BG (z-score)
- `X_profile`: shape `[4]` — `[weight_kg, fasting_bg, activity, diabetes_type]` (앞 2개 z-score, 뒤 2개 정수)
- `y`: shape `[24]` — 향후 120분 BG (z-score)
- `user_id`: 메타 (split 검증용)

### Scaler (`ai/models/scaler.pkl`)

dict 형태로 통합 저장:
```python
{
    "model1_features": StandardScaler(),  # carbs, current_glucose, fasting_bg, weight_kg
    "bg_target": StandardScaler(),         # BG_5min ~ BG_120min (24개 동일 분포 가정 → 단일 스칼라)
    "model2_seq": StandardScaler(),        # 시계열 BG (model1의 bg_target 과 동일하게 가는 게 일관)
    "model2_profile": StandardScaler(),    # weight_kg, fasting_bg
}
```

**유동성**: scaler 구조는 코드와 동기. 카테고리 의미(특히 meal_pattern)는 시뮬레이터 측 변경에 따라 동기 필요.

---

## 디렉토리 구조 (현 구조 기준, 유동적)

```
ai/
├── CLAUDE.md           ← 본 문서
├── PLAN.md
├── DATA_SPEC.md
├── EVAL_SPEC.md
├── PROMPTS.md
├── INTERFACE.md         ← backend 팀에 줄 시그니처
├── requirements.txt
├── pyproject.toml       ← 기존, 건드리지 않음
├── Dockerfile           ← 기존, 건드리지 않음
├── .env.example
├── .gitignore
│
├── app/                 ← 메인 코드 위치 (현 구조)
│   ├── main.py          ← FastAPI 엔트리포인트 (이미 존재)
│   ├── api/             ← FastAPI 라우터 (이미 존재, glucose.py 추가)
│   │   ├── __init__.py
│   │   └── glucose.py   ← /api/predict/glucose/{meal,now} 두 엔드포인트
│   ├── schemas/         ← Pydantic 모델 (이미 존재)
│   │   └── glucose.py   ← 요청/응답 스키마 (Model 1, Model 2 둘 다)
│   ├── glucose/         ← 본 모듈 (추론 코드), 새로 생성
│   │   ├── __init__.py
│   │   ├── data_loader.py        ← Model 1 + Model 2 Dataset
│   │   ├── food_carb_map.py     ← 이미 작성됨
│   │   ├── model.py             ← Model 1 (Ridge/MLP/LSTM-decoder) + Model 2 (LSTM time-series)
│   │   ├── train_base.py        ← Model 1 학습
│   │   ├── train_timeseries.py  ← Model 2 학습
│   │   ├── personalize.py
│   │   ├── predict.py           ← 두 모델 추론 (Predictor 두 개)
│   │   ├── evaluate.py
│   │   ├── llm_report.py
│   │   ├── calibration.py
│   │   ├── interface.py         ← api/glucose.py가 호출 (predict_meal_response, predict_now)
│   │   └── config.py            ← 모드 전환 등 설정
│   ├── models/          ← 모델 가중치 (음식 분류와 공유)
│   │   └── glucose/     ← 본 모듈 가중치는 여기
│   └── ...              ← 다른 팀원 코드 (음식 분류 등)
│
├── data/
│   ├── glucose_sample_data.csv  ← 전처리된 Model 1 sample (이전 32 컬럼, 새 schema는 33 컬럼)
│   ├── processed/                ← 전처리 출력
│   │   ├── train.csv             ← Model 1 (33 컬럼)
│   │   ├── val.csv
│   │   ├── test.csv
│   │   └── timeseries/           ← Model 2 (.npz)
│   │       ├── train.npz
│   │       ├── val.npz
│   │       └── test.npz
│   └── demo/                     ← 시연용 사전 계산 JSON
│
├── models/                ← 모델 가중치 + scaler
│   ├── scaler.pkl                ← Model 1, Model 2 통합 scaler dict
│   ├── ridge_meal.pkl            ← Model 1 Ridge
│   ├── mlp_meal.pt               ← Model 1 MLP
│   ├── lstm_meal.pt              ← Model 1 LSTM-decoder
│   └── lstm_now.pt               ← Model 2 LSTM time-series
│
├── reports/             ← 평가 결과 PNG/PDF
│
└── scripts/
    ├── check_env.py             ← 환경 검증
    ├── preprocess.py            ← Model 1 전처리 (이미 작성됨, meal_time sin/cos + BG 정규화 추가 수정 필요)
    ├── prepare_timeseries.py    ← Model 2 전처리 (신규, glucose_readings sliding window)
    ├── inspect_simulator.py     ← 시뮬레이터 컬럼 확인
    └── generate_demo.py
```

**유동성**:
- `app/glucose/` 위치는 팀과 합의 후 변경 가능
- 본 문서에 적힌 경로와 다르면, 작업 시작 시 결정하고 본 문서 업데이트
- 원본 시뮬레이터 데이터(`simuldate_params/`)는 ai/ 외부에 위치. preprocess.py 의 `--data-dir` 인자로 경로 변경 가능

---

## 기술 스택

- Python 3.11
- PyTorch (LSTM, GPU 사용)
- scikit-learn (Ridge baseline)
- pandas, numpy, openpyxl, xlrd
- Anthropic Claude API (LLM 리포트)
- loguru (로깅), python-dotenv (환경변수)
- matplotlib (시각화)

**GluPredKit은 사용하지 않음** (직접 PyTorch로 더 가볍게).

---

## 코드 스타일

- 타입 힌트 필수
- docstring (Google 스타일, 한국어 OK)
- 로깅: loguru (print 금지)
- 환경변수: python-dotenv
- 에러 핸들링: 의미 있는 메시지 + 로그
- 변수명/함수명 영어, 주석 한국어 OK
- 함수 단일 책임, 길어지면 분리

---

## 절대 하지 말 것

- Transformer, TFT, Mamba 등 추가 X (LSTM encoder-decoder까지만)
- 새 데이터셋 도입 X (시뮬레이터 + Shanghai만)
- web scraping X
- 음식 사진 처리 X (다른 모듈 영역)
- pyproject.toml, Dockerfile 수정 X (팀 합의 후)
- **새 FastAPI 앱 만들지 말 것**. 기존 `ai/app/main.py` 에 라우터 추가만 (`ai/app/api/glucose.py`)

---

## 작업 시작 체크리스트

새 모듈/기능 시작 전 항상:

- [ ] `scripts/check_env.py` 실행 → 환경 OK
- [ ] `scripts/inspect_simulator.py` 실행 → 시뮬레이터 출력 확인
- [ ] 출력 컬럼이 본 문서 스키마와 일치하는지 확인
- [ ] 다르면 본 문서 + DATA_SPEC.md 업데이트
- [ ] 그 다음 PROMPTS.md 순서대로 진행

---

## 변경 이력

| 날짜 | 변경 내용 | 사유 |
|---|---|---|
| 2026-04-28 | 초기 작성 | - |
| 2026-04-29 | FastAPI 정책 변경: ai/app/api/glucose.py 에 라우터 추가 (Jira 티켓 278 스펙 반영) | API 엔드포인트가 본 모듈 책임으로 이관 |
| 2026-04-29 | 데이터 schema 전면 개정: 시계열(12 timestep × 5 channel → 8 horizon) → meal-event(8 features → 24 BG outputs) | `glucose_sample_data.csv` 실제 형식 반영. 시뮬레이터가 약물 정보를 분리 출력하지 않고 diabetes_type 카테고리로 통합 |
| 2026-04-29 | 모델 사양 변경: LSTM time-series → Ridge/MLP/LSTM(enc-dec) 3-way 비교 | meal-event 회귀 패러다임에 맞춤 |
| 2026-04-29 | 데이터 소스 정정: 3-file 원본(`simuldate_params/simulate/`) + `preprocess.py` 이미 존재 | 사용자가 실제 파이프라인 공유. Shanghai 통합 보류. 인코딩 정정: T1D=0, T2D=1, Normal=2 / activity low/med/high → 0/1/2 / meal_pattern 11가지 (0~10) |
| 2026-04-29 | 분할 전략 변경: sim_train/sim_val/shanghai_* → 환자 단위 70/15/15 (train/val/test) | 시뮬레이터 단일 소스 + 일반화 평가 |
| 2026-04-29 | **Model 2 추가** (현재 시점 → 향후 120분 시계열 forecasting). 엔드포인트 `/meal`, `/now` 분리 | Jira 스펙의 `recent_values` 기반 예측 + 사용자 요구 ("음식 선택 시" + "현재 시점" 두 가지 기능 모두 필요) |
| 2026-04-29 | meal_time 단순 hour float → **sin/cos cyclic 변환** (입력 dim 8 → 9) | 23시-1시 가까움 표현. preprocess.py 수정 필요 |
| 2026-04-29 | BG 출력 (24개) **z-score 정규화** 추가. 추론 시 inverse_transform | LSTM/MLP 학습 안정화 |
| 2026-04-29 | scaler.pkl 구조 변경: 단일 StandardScaler → dict (Model 1 features / BG target / Model 2 seq / Model 2 profile) | 두 모델 + 출력 정규화 통합 |
