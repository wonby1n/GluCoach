# 평가 자동화 명세

> ⚠️ **2026-04-29 개정**: 데이터가 meal-event 단위 + 시계열 두 가지로 분리됨. 환자 단위 70/15/15 split. 이전 sim/Shanghai 시나리오 표는 폐기.

---

## 평가의 목적

1. **모델 1 선택 근거**: Ridge vs MLP vs LSTM(encoder-decoder)
2. **모델 2 검증**: 시계열 forecasting 의 정확도
3. **환자 일반화**: train/val/test가 환자 단위로 분리된 조건에서 unseen 환자 예측 정확도
4. **임상 안전성**: Clarke EG로 "이 예측 믿을만한가" 답변
5. **(옵션) 개인화 효과**: base vs personalized — 시간/데이터 충분하면

---

## 평가 시나리오

### Model 1 (식사 시점 → 식후 120분)

| ID | 모델 | 학습 | 평가 | 목적 |
|---|---|---|---|---|
| **M1-R** | Ridge | train.csv | test.csv | 단순 baseline |
| **M1-MLP** | MLP | train.csv | test.csv | 비선형 효과 |
| **M1-LSTM** | LSTM (encoder-decoder) | train.csv | test.csv | 시간 일관성 효과 ⭐ |
| (선택) M1-Pers | LSTM personalized | train + 특정 환자 | 해당 환자 test | 개인화 효과 |

핵심 비교:
- M1-R vs M1-MLP: 비선형 회귀가 도움 되는가?
- M1-MLP vs M1-LSTM: LSTM decoder 가 곡선 부드러움/일관성에 도움 되는가?

### Model 2 (현재 시점 → 향후 120분, 식사 없음 가정)

| ID | 모델 | 학습 | 평가 | 목적 |
|---|---|---|---|---|
| **M2-LSTM** | NowLSTM | timeseries/train.npz | timeseries/test.npz | 시계열 forecasting 검증 |

> Model 2 는 baseline 비교 없이 단일 모델 평가 (LSTM 만으로 충분, 시간 절약).
> 별도 baseline 필요하면 단순 "마지막 값 유지" (last-value baseline) 와 비교 가능.

> 데이터 규모가 작으면 (~229 식사 이벤트 / ~3000 시계열 윈도우) personalized 와 baseline 비교는 의미 제한적.

---

## 평가 지표

### 1. RMSE / MAE (mg/dL)
- **24 horizon 별** 계산 (BG_5min ~ BG_120min)
- **발표 핵심**: 30분, 60분, 120분 horizon

### 2. Clarke Error Grid (CEG)
- A+B 영역 비율 (%) — horizon 별
- A: 임상적으로 정확
- B: 임상적으로 무해한 오차
- 90% 이상 → 우수, 95% 이상 → 임상 적용 수준
- **발표 강조 horizon**: 30분 / 60분

### 3. 피크값 오차 (Peak BG Error, mg/dL)
- 24 출력 시퀀스의 argmax 값 오차
- |peak_pred - peak_true| 평균

### 4. 피크 시점 오차 (Peak Time Error, min)
- argmax 인덱스 × 5분 의 오차
- |t_peak_pred - t_peak_true| 평균

---

## Clarke Error Grid 직접 구현 가이드

methcomp 등 외부 라이브러리에 Clarke EG는 없음. 직접 구현.

### 영역 정의
- 가로축: reference glucose (true), 세로축: predicted glucose
- **Zone A**: |error| ≤ 20% of true value (또는 둘 다 < 70 mg/dL)
- **Zone B**: A 밖이지만 임상적으로 무해한 오차
- **Zone C**: 잘못된 치료를 유발할 수 있는 오차
- **Zone D**: 위험한 저혈당/고혈당을 놓침
- **Zone E**: 정반대 진단 (저혈당을 고혈당으로 등)

자세한 영역 식은 1987 Clarke 원논문 정의 활용.

### 시각화 사양
- 배경: 흰색
- A 영역: 연한 초록 (#d4f4dd)
- B 영역: 연한 노랑 (#fff4cc)
- C/D/E: 연한 빨강 (#ffcccc) — 점점 진하게 가능
- 점: 검은색 산점도, alpha=0.3, size=10
- 그리드: 회색 점선
- 폰트: sans-serif, 12pt
- 제목: "Clarke Error Grid - {Model Name} (PH={horizon}min)"
- 축 범위: 0~400 mg/dL
- 대각선 (y=x): 회색 점선

---

## 평가 자동화 코드 구조

`app/glucose/evaluate.py`:

```python
LABEL_STEPS = list(range(5, 125, 5))  # 24개
KEY_HORIZONS = [30, 60, 120]

def evaluate_model(model_path, test_csv, model_type) -> dict:
    """
    Returns:
        {
            "model_name": str,
            "model_type": "ridge" | "mlp" | "lstm",
            "horizons_min": [5, 10, ..., 120],
            "rmse_per_horizon": [float; 24],
            "mae_per_horizon": [float; 24],
            "ceg_a_plus_b_per_horizon": [float; 24],
            "peak_value_error_mg_dl": float,
            "peak_time_error_min": float,
            "n_samples": int,
        }
    """

def plot_clarke_error_grid(y_true, y_pred, horizon_min, save_path):
    """Clarke EG 산점도 + 영역 표시 PNG."""

def plot_predictions_curve(y_true_seq, y_pred_seq, save_path):
    """24-step 식후 곡선 비교 (라인 플롯)."""

def plot_meal_response_examples(test_data, predictions, n_examples, save_path):
    """test set 무작위 N건의 곡선 비교."""

def generate_comparison_table(results: List[dict], save_path):
    """모델 × KEY_HORIZONS RMSE/MAE/CEG 표 PNG."""
```

`scripts/run_all_evaluations.py`:
- S1/S2/S3 시나리오 자동 실행
- `reports/` 폴더에 모든 산출물 저장

---

## 출력 산출물

```
reports/
├── scenarios/
│   ├── M1-R_clarke_30min.png
│   ├── M1-R_clarke_60min.png
│   ├── M1-MLP_clarke_30min.png
│   ├── M1-MLP_clarke_60min.png
│   ├── M1-LSTM_clarke_30min.png
│   ├── M1-LSTM_clarke_60min.png
│   ├── M2-LSTM_clarke_30min.png
│   └── M2-LSTM_clarke_60min.png
├── final_comparison_table.png        ← 발표용 (Model 1 3개 + Model 2 별도)
├── clarke_grid_meal_30min.png        ← 발표용 (Model 1 의 3 모델 나란히)
├── clarke_grid_now_30min.png         ← 발표용 (Model 2 단일)
├── meal_response_examples.png        ← Model 1 의 식후 곡선 5건
└── now_forecast_examples.png         ← Model 2 의 시계열 forecasting 5건
```

---

## 발표 슬라이드 자동 매핑

| 슬라이드 메시지 | 사용 그림 |
|---|---|
| "두 가지 예측 시나리오 (식사 / 일반 시점)" | 두 엔드포인트 다이어그램 |
| "왜 Ridge로는 부족한가?" | M1-R vs M1-MLP RMSE 표 |
| "LSTM decoder 의 효과는?" | M1-MLP vs M1-LSTM 24-horizon RMSE 곡선 |
| "현재 시점 예측 정확도?" | M2-LSTM Clarke EG + 곡선 예시 |
| "임상적 안전성?" | M1-LSTM, M2-LSTM Clarke EG (A+B %) |
| (옵션) "개인화 효과?" | M1-LSTM vs M1-Pers 표 |

---

## 시간 부족 시 우선순위

1. **필수**: M1-R, M1-LSTM, M2-LSTM
2. **추천**: M1-MLP — 비선형 효과 비교
3. **선택**: M1-Pers (개인화) — 데이터 적어 효과 보장 X
4. **선택**: 피크값/피크시점 오차 — 시간 남으면

---

## 작은 데이터 주의

- 시뮬레이터 데이터 증량 전: test.csv 가 ~30~40건 (108명 * 15% * 2식/명) → RMSE 통계적 noise 큼
- Model 2 timeseries test 도 윈도우 기준 ~500건 가능성. 환자 16명 × 윈도우 ~30 = ~480
- 데이터 늘어나면 자연 해결. 그 전엔 RMSE 결과를 "참고용" 으로 표기
- bootstrap 으로 95% CI 표시 권장 (시간 되면)
- seed 5개 평균 권장 (시간 되면)
