# 혈당 예측 모델 보고서

업데이트: 2026-05-14  
현재 배포 모델: **ShanghaiLSTM** (T2DM-only, carb_coef=1.5)

---

## 한눈에 보는 결과 (T2DM 테스트셋 339건)

| 모델 | MAE@30min | RMSE@30min | 피크 오차 | CEG A+B@30min |
|------|----------|------------|---------|--------------|
| **ShanghaiLSTM** (배포) | **33.6 mg/dL** | **43.1 mg/dL** | **31.2 mg/dL** | **97.3%** |
| Stage2 XGBoost (폴백) | 43.1 mg/dL | 54.3 mg/dL | 42.2 mg/dL | 91.7% |

> 출처: `eval/t2dm_compare/results.json`

---

## 1. 학습 데이터

| 항목 | 내용 |
|------|------|
| 데이터셋 | Shanghai T2DM 임상 데이터 (공개) |
| 대상 | T2DM + Normal 환자 (**T1DM 제외**) |
| 분할 | 환자 단위 70/15/15 (seed=42) |
| 테스트셋 | 339건 |

**T1DM 제외 이유**: T1DM 환자는 인슐린 주사 타이밍이 혈당 곡선을 지배해 음식→혈당 신호가 인슐린 노이즈에 묻힘. T2DM/Normal은 내인성 인슐린이 일관되게 반응하므로 음식 영양소→혈당 반응 학습이 가능.

---

## 2. 입력 특징 (11개)

| # | 특징 | 비고 |
|---|------|------|
| 1–4 | 탄수화물, 단백질, 지방, 식이섬유 (g) | |
| 5 | 열량 (kcal) | |
| 6 | 식전 혈당 (mg/dL) | |
| 7–8 | 식사 시각 sin / cos | 24시간 주기 표현 |
| 9 | 당뇨 유형 | Embedding (Normal=0, T1D=1, T2D=2) |
| 10 | 탄수화물 비율 | carbs / (carbs+protein+fat) |
| 11 | 단백질+지방 합계 | |

> 코드: `ai/app/glucose/shanghai_lstm.py`, `ai/scripts/train_stage2_lstm.py`

---

## 3. 모델 아키텍처

### 두 단계 예측

```
식사 정보 입력 (11개 특징)
    ↓
선형 Prior (공식 기반 뼈대 곡선)
    + LSTM 잔차 (선형 공식 오차 보정)
    + 식전 혈당 (절댓값 복원)
    ↓
식후 5~120분 혈당 24개 시점 (mg/dL)
```

**선형 Prior 공식** (`compute_prior_delta_row`):
```
피크 상승량 = max(0, 10 + 탄수화물 × 1.5 − 식이섬유 × 1.5 − 지방 × 0.3 − 단백질 × 0.2)
```

**ShanghaiLSTM 구조** (hidden=64, dropout=0.2):

```
입력 (11개)
    ↓
dtype_embed:  Embedding(3, 8)          ← 당뇨 유형
encoder:      Linear(10+8, 64) → ReLU → Dropout
step_embed:   Embedding(24, 16)         ← 시점 위치 인코딩
LSTMCell:     (64+16) → 64             ← 시점별 반복 (24회)
head:         Dropout → Linear(64, 1)
    ↓
출력 (24개 BG 잔차) → 역정규화 + Prior + 식전 혈당 = 최종 혈당
```

> 코드: `ai/app/glucose/shanghai_lstm.py`

---

## 4. 학습 설정

| 항목 | 값 | 코드 위치 |
|------|-----|---------|
| 최대 에폭 | 300 | `train_stage2_lstm.py` |
| 배치 크기 | 32 | `train_stage2_lstm.py` |
| 학습률 | 1e-3 | `train_stage2_lstm.py` |
| LR 스케줄러 | CosineAnnealingLR | `train_stage2_lstm.py` |
| 조기 종료 patience | 30 | `train_stage2_lstm.py` |
| 손실 함수 | Peak-weighted MSE (30~90분 구간 3×) | `train_stage2_lstm.py` |

> T2DM 전용 필터링 및 carb_coef=1.5 설정: `ai/scripts/train_t2dm_lstm.py`

---

## 5. 성능 평가

### 시점별 오차 (T2DM 테스트셋, 339건)

| 예측 시점 | MAE | RMSE | CEG A+B |
|---------|-----|------|---------|
| 5분 | 8.8 mg/dL | 12.0 mg/dL | 98.2% |
| 30분 | 33.6 mg/dL | 43.1 mg/dL | 97.3% |
| 60분 | 34.2 mg/dL | 45.1 mg/dL | 95.6% |
| 120분 | 29.5 mg/dL | 39.4 mg/dL | 94.4% |

| 지표 | 값 |
|------|-----|
| 피크 값 오차 (MAE) | 31.2 mg/dL |
| 피크 시점 오차 | 27.1 분 |

> 출처: `eval/t2dm_compare/results.json`

---

## 6. 배포 파일

```
ai/models/
├── lstm_meal_t2dm_coef15.pt      ← 현재 배포 모델 (ShanghaiLSTM 가중치)
├── lstm_t2dm_scaler_coef15.pkl   ← 특징/BG 스케일러 + carb_coef(1.5) 저장
├── lstm_now.pt                   ← 현재 시점 예측 모델 (NowLSTM)
└── stage2_meal/                  ← XGBoost 폴백 모델
```

Docker named volume `s309-ai-models`으로 재시작 후에도 유지됨.

---

## 7. 서버 추론 흐름

추론 우선순위 (`ai/app/glucose/interface.py`):
1. **ShanghaiLSTM** — 개인화 모델(`lstm_meal_personalized_{user_id}.pt`) 있으면 자동 우선 사용
2. **Stage2 XGBoost** — LSTM 파일 없을 때 폴백
3. **Dummy 휴리스틱** — 둘 다 없을 때 (carbs × 0.6 = 피크 상승)

Confidence: 정상 추론 0.85 / dummy 폴백 0.3

---

## 8. 개인화 (미연결)

- AI 서버 엔드포인트 구현 완료: `POST /inference/glucose/personalize`
- **백엔드에서 아직 호출하지 않음**
- 동작 방식: base 모델을 사용자 데이터로 fine-tuning (최소 10건), RMSE@30 개선 없으면 자동 폐기
- 저장 위치: `models/lstm_meal_personalized_{user_id}.pt`

> 코드: `ai/app/glucose/personalize.py`
