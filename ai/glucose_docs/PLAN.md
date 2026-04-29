# 실행 계획

> **본 계획은 가이드일 뿐. 막히면 우선순위 조정 가능.**
> **2026-04-29 개정**: Model 1 (식사 시점) + Model 2 (현재 시점) 두 개 병행. 시뮬레이터 데이터 증량 대기 중.

---

## Phase 0: 환경 + 브랜치 + 데이터 수령

- [ ] **git 브랜치 생성**: `ai/feature-glucose-predict-api-S14P31S309-278`
- [ ] **scripts/check_env.py 작성/실행** → Python/GPU/.env/의존성 OK 확인
- [ ] 시뮬레이터 팀에 데이터 증량 요청 (별도 문서)
- [ ] 증량된 데이터(`simuldate_params/simulate/`) 수령 후 위치 확인

**예상 시간**: 30분 + (데이터 대기 시간)

---

## Phase 1: 데이터 준비

### Model 1 (meal-event)
- [ ] **P1-1**: `scripts/preprocess.py` 수정 (이미 있는 코드 보완)
  - meal_time → meal_time_sin, meal_time_cos cyclic 변환 추가
  - BG_5min~BG_120min 24개 z-score 정규화 추가 (단일 통합 scaler)
  - scaler.pkl 구조를 dict 로 변경 (`model1_features, bg_target, profile`)
  - 환자 단위 split 결과의 user_id 리스트를 별도 메타 파일로 저장 (Model 2 가 재사용)
- [ ] **P1-2**: 실행 + 검증
  - 33 컬럼 출력
  - 환자 분리 검증 (교집합 = 공집합)
  - 분포 검증 (정규화 4개 평균≈0/std≈1, sin/cos [-1,1], BG 평균≈0/std≈1)

### Model 2 (시계열)
- [ ] **P1-3**: `scripts/prepare_timeseries.py` 신규 작성
  - glucose_readings.csv + meal_events.csv 로드
  - 5분 grid reindex + ffill
  - sliding window (60min 입력 + 120min 출력 = 36 timestep, stride 6)
  - 윈도우 안에 식사 이벤트 있으면 제외
  - **Model 1 의 user_id split 메타 파일을 로드해서 동일하게 적용**
  - scaler.pkl 의 bg_target 재사용 (X_seq, y 정규화), profile 재사용 (X_profile)
  - 출력: `data/processed/timeseries/{train,val,test}.npz`

### DataLoader
- [ ] **P1-4**: `app/glucose/data_loader.py`
  - `MealDataset(csv_path, return_categorical_separately)` — Model 1 용
  - `TimeseriesDataset(npz_path)` — Model 2 용
  - 첫 배치 shape 확인 테스트

**예상 시간**: 2시간 (preprocess.py 수정 0.5h + prepare_timeseries 신규 0.7h + DataLoader 0.5h + 검증 0.3h)
**완료 기준**: 두 모델 모두 학습 가능한 데이터 셋 준비 완료

---

## Phase 2: 모델 학습

### Model 1 (식사 시점)
- [ ] **P2-1**: `app/glucose/model.py` — Model 1 모델 3종 정의
  - `MealRidge`: sklearn Ridge + MultiOutputRegressor (9 → 24, 카테고리 one-hot)
  - `MealMLP`: 9 (5 cont + 3 cat embedding 합쳐서) → 64 → 64 → 24
  - `MealLSTMDecoder`: encoder Linear → LSTM decoder 24 step
- [ ] **P2-2**: `app/glucose/train_base.py` — Model 1 학습 (Ridge / MLP / LSTM 모두)
  - val RMSE 기준 best 모델 저장
  - 저장 경로: `ai/models/{ridge_meal,mlp_meal,lstm_meal}.{pkl,pt}`

### Model 2 (현재 시점)
- [ ] **P2-3**: 같은 model.py 에 Model 2 모델 추가
  - `NowLSTM`: nn.LSTM(input_size=1+4 stacked, hidden=64, layers=2) → Linear(hidden, 24)
    - 시계열 12 timestep 에 매 timestep마다 profile 4개를 concat (12 × 5)
    - 또는 LSTM 마지막 hidden + profile concat → Linear
- [ ] **P2-4**: `app/glucose/train_timeseries.py` — Model 2 학습
  - 저장: `ai/models/lstm_now.pt`

### (선택)
- [ ] **P2-5**: 개인화 fine-tune (시간/데이터 충분하면)

**예상 시간**: 3시간 (학습 시간 포함)
**완료 기준**: 4개 모델 파일 (`ridge_meal.pkl, mlp_meal.pt, lstm_meal.pt, lstm_now.pt`)

---

## Phase 3: 평가

- [ ] **P3-1**: `app/glucose/evaluate.py`
  - 24 horizon별 RMSE/MAE
  - Clarke EG A+B 비율 (직접 구현, 외부 라이브러리 X)
  - 핵심 horizon: 30/60/120분
  - **Model 1, Model 2 모두 같은 평가 함수**
- [ ] **P3-2**: `scripts/run_all_evaluations.py`
  - Model 1 시나리오 (Ridge/MLP/LSTM × test.csv)
  - Model 2 시나리오 (LSTM × timeseries/test.npz)
  - 모델 간 비교는 직접적이지 않음 (다른 입력) — 각각 best 모델 선정만
- [ ] **P3-3**: 비교 표 + 시각화 → `reports/`

**예상 시간**: 1.5시간
**완료 기준**: `reports/final_comparison_table.png`, `reports/scenarios/*.png`

---

## Phase 4: 활용 모듈 + API

- [ ] **P4-1**: `app/glucose/predict.py`
  - `MealPredictor`: Model 1 추론 (raw 입력 → encoding → scaler → forward → inverse → 24 BG)
  - `NowPredictor`: Model 2 추론 (recent_values → scaler → forward → inverse → 24 BG)
  - 모듈 레벨 lazy 싱글톤
- [ ] **P4-2**: `app/glucose/llm_report.py` — LLM 코멘트 + 주간 리포트
- [ ] **P4-3**: `app/glucose/calibration.py` — 선형회귀 캘리브레이션 (raw signal → glucose)
- [ ] **P4-4**: `app/glucose/interface.py` — 두 함수 노출
  - `predict_meal_response(req: dict) -> dict` (Model 1)
  - `predict_now(req: dict) -> dict` (Model 2)
- [ ] **P4-5**: FastAPI 라우터 (Jira 278 본체)
  - `app/schemas/glucose.py` — Pydantic 스키마 두 세트 (MealRequest/Response, NowRequest/Response)
  - `app/api/glucose.py`:
    - `POST /api/predict/glucose/meal` → interface.predict_meal_response
    - `POST /api/predict/glucose/now` → interface.predict_now
    - `GET /api/predict/glucose/health`
  - `app/main.py` 에 include_router

**예상 시간**: 3시간
**완료 기준**: `/docs` 에서 두 엔드포인트 호출 성공

---

## Phase 5: 시연 자료

- [ ] **P5-1**: `app/glucose/config.py` — 시연 모드 설정
- [ ] **P5-2**: `scripts/generate_demo.py` — 김민수 페르소나 하루 데이터 사전 계산
  - 식사 4회 (Model 1) + 식사 사이 forecasting (Model 2) 모두 사전 계산
- [ ] **P5-3**: `INTERFACE.md` — 백엔드 팀에 줄 API 문서

**예상 시간**: 1.5시간

---

## 시간 배분 요약

| Phase | 예상 시간 | 누적 |
|---|---|---|
| Phase 0 | 0.5h | 0.5h |
| Phase 1 | 2.0h | 2.5h |
| Phase 2 | 3.0h | 5.5h |
| Phase 3 | 1.5h | 7.0h |
| Phase 4 | 3.0h | 10.0h |
| Phase 5 | 1.5h | 11.5h |
| 버퍼 | 1.0h | 12.5h |

**현실적 작업량**: 10~13시간. Model 2 추가로 약 2~3시간 늘어남.

---

## 우선순위 (시간 부족 시 자르는 순서)

1. ❌ **개인화 (P2-5)** — base 모델로도 발표 가능
2. ❌ **Model 2 의 Ridge/MLP 비교** — Model 2는 LSTM만으로 충분
3. ❌ **주간 리포트 (P4-2 일부)** — 식사 코멘트만 있어도 OK
4. ❌ **캘리브레이션 (P4-3)** — 발표에서 안 보여줘도 됨
5. ❌ **시연 데이터 사전 계산 (P5-2)** — 라이브 호출로도 가능 (불안정성 감수)

자르고 자르면 핵심만 7시간 정도.

---

## 막히면

- 학습 안 떨어짐 → lr 1e-3 → 5e-4 → 1e-4 순차 시도
- OOM → batch_size 절반으로
- 시뮬레이터 컬럼 다름 → DATA_SPEC.md 업데이트 → preprocess.py 매핑 수정
- 학습 데이터 부족 → 데이터 증량 대기. 일단 현재 데이터로 baseline 확보
- Model 2 학습 윈도우 부족 (식사가 너무 잦아서 윈도우 다 잘림) → stride 줄이기 또는 윈도우 길이 단축
- LLM API 실패 → fallback 메시지
- Clarke EG 그리기 막힘 → RMSE만 먼저 표시
