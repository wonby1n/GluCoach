# 혈당 예측 모델 학습 계획

> 작성일: 2026-05-01
> 목적: 모델 재설계 및 데이터 전략 수립

---

## 1. 목표 성능 기준 (임상 기준)

| 지표 | 허용 | 우수 | 임상 등급 |
|------|------|------|----------|
| RMSE @30min | ≤ 20 mg/dL | ≤ 15 mg/dL | ≤ 10 mg/dL |
| RMSE @60min | ≤ 25 mg/dL | ≤ 20 mg/dL | ≤ 15 mg/dL |
| Clarke A+B @30min | ≥ 90% | ≥ 95% | ≥ 98% |
| Clarke A+B @60min | ≥ 85% | ≥ 90% | ≥ 95% |

---

## 2. 데이터 현황

### 2-1. sim_v3 (주 학습 데이터)

- 위치: `ai/data/glucose_data/sim_data/sim_v3/`
- 구성: 108개 카테고리 × 330명 = **35,640명**
- 데이터 길이: 유저당 **6일치** (5분 간격 → 약 1,728 readings/명)
- sim_v1은 동일 카테고리의 55명 × 1일치로 v3의 부분집합 수준 → **사용하지 않음**

### 2-2. Shanghai (실제 임상 데이터)

- 위치: `ai/data/glucose_data/Shanghai/`
- T1DM: 6명 (세션 19개), T2DM: 75명 (세션 109개)
- 강점: 실제 환자 데이터
- 약점: Normal(정상인) 없음, activity 정보 없음(→ 0으로 처리), 데이터 불규칙
- 전처리 스크립트: `scripts/preprocess_shanghai.py` + `scripts/food_carb_map.py`

### 2-3. 데이터 규모 예상 (전처리 후)

| 소스 | Model 1 식사 이벤트 | Model 2 시계열 윈도우 |
|------|--------------------|--------------------|
| sim_v3 | ~250,000건 이상 | ~수백만 윈도우 |
| Shanghai | ~2,000~5,000건 | ~수천 윈도우 |

---

## 3. 실험 설계

### 3-1. Step 1 — 아키텍처 선정 (sim_v3 단일 소스)

> 목적: 데이터 전략과 무관하게 어떤 모델 구조가 최적인지 먼저 결정

| 실험 ID | 모델 | 학습 | 평가 |
|---------|------|------|------|
| E1-Ridge | Ridge MultiOutput | sim_v3 train (70%) | sim_v3 test (15%) |
| E1-MLP | MealMLP | sim_v3 train | sim_v3 test |
| E1-LSTM | MealLSTMDecoder | sim_v3 train | sim_v3 test |

**선정 기준**: RMSE @30min 가장 낮은 모델 → 이하 `Best`로 고정

### 3-2. Step 2 — 데이터 전략 비교 (`Best` 아키텍처 고정)

| 실험 ID | 학습 데이터 | 평가 데이터 | 알 수 있는 것 |
|---------|------------|------------|--------------|
| E2-SimOnly | sim_v3 | sim_v3 test | 순수 sim 성능 (베이스라인) |
| E2-SimEvalReal | sim_v3 | Shanghai 전체 | sim→real 갭 측정 |
| E2-Mixed | sim_v3 + Shanghai | sim_v3 test | 혼합이 sim 성능에 영향 주는지 |
| E2-MixedReal | sim_v3 + Shanghai | Shanghai test | 혼합이 real 성능 향상시키는지 |
| E2-RealOnly | Shanghai | sim_v3 test | 역방향: real→sim 일반화 (참고용) |

> **주의**: E2-RealOnly는 Shanghai 데이터가 적어 overfitting 가능성 높음. 결과가 나빠도 당연함.

> **scaler 정책**: sim_v3 기준으로 fit한 scaler를 Shanghai에도 적용 (Shanghai로 재fit 금지 — 추론 시 일관성 파괴).

### 3-3. Step 3 — Model 2 (NowLSTM)

| 실험 ID | 학습 데이터 | 평가 | 비고 |
|---------|------------|------|------|
| E3-SimOnly | sim_v3 sliding window | sim_v3 test | 기본 |
| E3-Mixed | sim_v3 + Shanghai CGM | sim_v3 test | Shanghai는 carbs 불필요 → 활용 가능 |

### 3-4. Step 4 — 개인화 효과 검증 (선택)

- Best Model 1 base vs personalized (RMSE @30min 비교)
- 개인화 데이터: Shanghai 환자 1명의 이력을 fine-tuning seed로 활용

---

## 4. 전처리 파이프라인

```
Step 0. 환경 확인
  └── python scripts/check_env.py

Step 1. sim_v3 전처리 (Model 1)
  └── python scripts/preprocess.py --data-dir data/glucose_data/sim_data/sim_v3
      출력: data/processed/{train,val,test}.csv + models/scaler.pkl + models/user_split.json

Step 2. sim_v3 시계열 전처리 (Model 2)
  └── python scripts/prepare_timeseries.py
      출력: data/processed/timeseries/{train,val,test}.npz

Step 3. Shanghai 전처리 (E2 실험용)
  └── python scripts/preprocess_shanghai.py
      출력: data/processed/shanghai_{train,val,test}.csv (별도 저장, sim 파일 덮어쓰기 금지)
```

> preprocess.py의 --data-dir 인자가 sim_v3 경로를 가리키도록 수정 필요 (현재 구버전 경로로 되어있을 수 있음)

---

## 5. 학습 순서

```
Phase 1. E1 아키텍처 선정 (E1-Ridge, E1-MLP, E1-LSTM)
  └── python scripts/train.py --model ridge
  └── python scripts/train.py --model mlp
  └── python scripts/train.py --model lstm
  → 결과 비교 후 Best 선정

Phase 2. E2 데이터 전략 비교 (Best 고정)
  └── E2-SimOnly: 이미 Phase 1에서 완료
  └── E2-SimEvalReal: sim 모델을 Shanghai test로 평가
  └── E2-Mixed: Shanghai 포함해서 재학습
  └── E2-MixedReal: 위 모델을 Shanghai test로 평가
  └── E2-RealOnly: Shanghai만으로 학습 (참고용)

Phase 3. E3 NowLSTM
  └── python -m app.glucose.train_timeseries

Phase 4. 전체 평가 리포트
  └── python scripts/run_all_evaluations.py
      출력: reports/ 폴더에 Clarke EG, RMSE 곡선, 비교 표
```

---

## 6. 의사결정 트리 (결과에 따른 발표 전략)

```
E2-SimEvalReal (sim 학습 → real 평가) 결과가
  ├── 충분히 좋음 (Clarke A+B ≥ 90%)
  │   → "시뮬 데이터로 학습했는데 실제 환자에도 잘 맞음"
  │   → sim-to-real 일반화 능력 강조
  │
  └── 형편없음 (Clarke A+B < 85%)
      → "시뮬 데이터의 한계, 개인화가 왜 필요한가"의 근거로 활용
      → E2-MixedReal과 비교: "소량 real 데이터 혼합으로 갭을 줄임" 강조

어느 쪽이든 발표에 쓸 수 있는 스토리가 생긴다.
```

---

## 7. 파일 관리 원칙

- 학습 완료된 모델: `ai/models/` (gitignore, 서버 직접 보관)
- 전처리 결과: `ai/data/processed/` (gitignore)
- sim용 scaler: `ai/models/scaler.pkl` (sim_v3 기준으로 fit)
- Shanghai 전처리 결과: `ai/data/processed/shanghai_*.csv` (sim CSV와 분리 저장)
- 평가 리포트: `ai/reports/`

---

## 8. 현재 상태

- [x] 구 모델 파일 삭제 (`ai/models/` 비움)
- [x] 구 전처리 결과 삭제 (`ai/data/processed/` 비움)
- [x] 구 평가 리포트 삭제 (`ai/reports/` 비움)
- [x] preprocess.py 경로 수정 (sim_v3 경로 반영)
- [x] 전처리 실행 (sim_v3 + Shanghai + Mixed)
- [x] E1 아키텍처 선정 실험 → **LSTM 선정** (RMSE@30: Ridge 11.44 / MLP 10.27 / LSTM 10.21)
- [x] E2 데이터 전략 비교 실험 (SimOnly / SimEvalReal / RealOnly / Mixed)
- [x] E3 NowLSTM 학습 → RMSE@30 10.07 mg/dL
- [x] 전체 평가 리포트 생성 (`docs/EVALUATION_REPORT.md`)
- [ ] Clarke 오류 격자 시각화 (`scripts/run_all_evaluations.py`)
- [ ] 개인화(파인튜닝) 검증
