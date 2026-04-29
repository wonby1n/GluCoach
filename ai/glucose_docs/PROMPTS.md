# 바이브 코딩 프롬프트 (순서대로 사용)

> 사용법:
> 1. 각 단계 시작 전 "사전 조건" 체크
> 2. "프롬프트" 그대로 복붙
> 3. 결과 확인 후 "성공 기준" 체크
> 4. 문제 시 "트러블슈팅" 참조
> 5. 다음 단계로

> ⚠️ **2026-04-29 개정 안내 (확정 버전)**
> - **두 모델 병행**:
>   - Model 1 (식사 시점): 9 features → 24 BG (식후 5~120분)
>   - Model 2 (현재 시점): 12 timestep BG + profile 4 → 24 BG (향후 5~120분)
> - 엔드포인트 분리: `POST /ai/predict/glucose/meal`, `POST /ai/predict/glucose/now`
> - 입력 dim 변경: meal_time → meal_time_sin, meal_time_cos (cyclic 변환, 입력 8 → 9)
> - **BG 출력 z-score 정규화**: 학습은 정규화된 값, 추론 시 inverse_transform
> - scaler.pkl 구조: dict (`model1_features, bg_target, profile`)
> - 인코딩: T1D=0, T2D=1, Normal=2 / activity low/med/high → 0/1/2 / meal_pattern 11가지 (0~10)
> - 분할: 환자 단위 70/15/15. Model 1 의 user_id split을 메타로 저장해서 Model 2 가 동일하게 적용
> - Shanghai 보류, 시뮬레이터 단일 소스
> - 자세한 schema 는 갱신된 `CLAUDE.md` / `DATA_SPEC.md` 참고

---

## P0: 시작 + 환경 검증

**사전 조건**:
- [ ] 다음 파일들이 ai/ 폴더에 있음: CLAUDE.md, PLAN.md, DATA_SPEC.md, EVAL_SPEC.md
- [ ] `simuldate_params/simulate/{users,meal_events,glucose_readings}.csv` 존재 (ai/ 외부)
- [ ] `ai/scripts/preprocess.py` 존재 (이미 작성됨)
- [ ] .env 에 ANTHROPIC_API_KEY 설정

**프롬프트**:
```
@CLAUDE.md @PLAN.md @DATA_SPEC.md @EVAL_SPEC.md 4개 문서 모두 읽고 프로젝트 전체 파악해줘.

그 다음 환경 검증 스크립트 실행:

1. scripts/check_env.py 만들어서 실행:
   - Python 버전, GPU/CUDA 가용성
   - .env 파일 존재 + ANTHROPIC_API_KEY 존재 확인
   - simuldate_params/simulate/ 의 3개 CSV 존재 확인
   - requirements.txt 의존성 모두 import 가능한지
   - 결과 콘솔에 깔끔하게 출력

질문 있으면 코딩 시작 전에 먼저 물어봐.
```

**성공 기준**:
- [ ] check_env.py 출력에서 모든 ✓

**트러블슈팅**:
- GPU 없음 → DEVICE=cpu로 .env 변경
- simuldate_params 없음 → 시뮬레이터 팀에서 데이터 받아 위치 확인

---

## P1-1: preprocess.py 수정 + 검증 (Model 1 데이터)

**사전 조건**:
- [ ] P0 완료
- [ ] `ai/scripts/preprocess.py` 기존 코드 존재
- [ ] `simuldate_params/simulate/` 데이터 수령 완료

**프롬프트**:
```
ai/scripts/preprocess.py 를 다음대로 수정해줘. **새로 짜지 말고 기존 코드 손대줘.**

수정 내용:
1. meal_time 컬럼 cyclic 변환:
   - 기존: hour = meal_time.hour + minute/60.0 (단일 컬럼)
   - 변경: meal_time_sin = sin(2*pi*hour/24), meal_time_cos = cos(2*pi*hour/24) (두 컬럼)
   - SCALE_COLS 에서 meal_time 제거 (sin/cos 는 이미 [-1,1] 범위)
   - SCALE_COLS = ["carbs", "current_glucose", "fasting_bg", "weight_kg"] (4개)
   - FEATURE_COLS = ["carbs", "meal_time_sin", "meal_time_cos", "current_glucose",
                     "fasting_bg", "weight_kg", "activity", "diabetes_type", "meal_pattern"] (9개)

2. BG 출력 정규화 추가:
   - LABEL_COLS = [f"BG_{t}min" for t in range(5, 125, 5)] (24개)
   - 별도 BG scaler: train 의 24개 컬럼 모두 flatten 해서 단일 StandardScaler.fit
     ```
     bg_train_flat = train[LABEL_COLS].values.reshape(-1, 1)
     bg_scaler = StandardScaler().fit(bg_train_flat)
     # transform 시: train[LABEL_COLS] = bg_scaler.transform(train[LABEL_COLS].values.reshape(-1,1)).reshape(N, 24)
     ```
   - val/test 도 같은 bg_scaler 로 transform

3. scaler.pkl 구조 변경 (dict 로):
   ```python
   scalers = {
       "model1_features": feature_scaler,  # 4개 컬럼
       "bg_target": bg_scaler,              # BG flatten 통계
   }
   pickle.dump(scalers, open("models/scaler.pkl", "wb"))
   ```

4. 환자 split 메타 저장 (Model 2 가 재사용):
   - models/user_split.json 에 {"train": [user_ids...], "val": [...], "test": [...]} 저장

5. 출력 컬럼 순서 (33개):
   [carbs, meal_time_sin, meal_time_cos, current_glucose, fasting_bg, weight_kg,
    activity, diabetes_type, meal_pattern,
    BG_5min, ..., BG_120min]

수정 후 실행:
  cd ai/
  python scripts/preprocess.py

검증:
- data/processed/{train,val,test}.csv 모두 33 컬럼
- models/scaler.pkl, models/user_split.json 생성
- train/val/test user_id 교집합 = 공집합
- 정규화 4개 컬럼 train 평균≈0/std≈1
- meal_time_sin/cos 가 [-1,1] 범위
- BG 24개 train 평균≈0/std≈1 (정규화 적용 확인)
- 카테고리 컬럼 값 범위 정확

DATA_SPEC.md 매핑과 다른 점 있으면 보고. 시뮬레이터 데이터 형식이 가정과 다르면 DATA_SPEC.md 갱신 후 진행.
```

**성공 기준**:
- [ ] CSV 33 컬럼
- [ ] scaler.pkl, user_split.json 생성
- [ ] 환자 교집합 = 공집합
- [ ] 분포 검증 통과

**트러블슈팅**:
- meal_pattern KeyError → users.csv 실제 값 확인 후 MEAL_PATTERN_MAP 추가
- 유효 레코드 너무 적음 → glucose_readings 시간 범위 확인. 식사 후 120분 짤리는 케이스 수 확인

---

## P1-2: prepare_timeseries.py (Model 2 데이터 신규)

**사전 조건**: P1-1 완료. `models/user_split.json`, `models/scaler.pkl` 존재.

**프롬프트**:
```
ai/scripts/prepare_timeseries.py 신규 작성. Model 2 시계열 데이터 추출.

전체 흐름:
1. 입력:
   - simuldate_params/simulate/glucose_readings.csv
   - simuldate_params/simulate/meal_events.csv
   - simuldate_params/simulate/users.csv
   - models/user_split.json (Model 1 의 환자 분할 재사용)
   - models/scaler.pkl (bg_target, profile/model1_features 재사용)

2. 처리:
   a. glucose_readings 유저별 5분 grid reindex + ffill (preprocess.py 와 동일)
   b. 각 유저별 meal_events 시각 set 만들기
   c. 시계열 위에서 sliding window 추출:
      - 윈도우 크기: 36 timestep (input 12 + output 24, 60min + 120min)
      - stride: 6 timestep (30분)
      - **윈도우 안에 식사 이벤트 시각이 있으면 제외**
   d. 각 유효 윈도우마다:
      - X_seq: 윈도우 0~11 (60min) BG, scaler.bg_target.transform 적용
      - X_profile: users.csv 에서 해당 user 의 weight_kg, fasting_bg (정규화), activity, diabetes_type (정수)
        * weight_kg, fasting_bg 정규화는 scaler.model1_features 일부만 사용 (인덱스 매핑 주의)
        * 또는 별도 scaler.profile 만들어 두고 scaler.pkl 갱신
      - y: 윈도우 12~35 (120min) BG, scaler.bg_target.transform
      - meta: user_id (split 검증용)
   e. user_split.json 의 분할 따라 train/val/test 그룹화

3. 저장:
   data/processed/timeseries/{train,val,test}.npz 에
   np.savez_compressed(path, X_seq=..., X_profile=..., y=..., user_ids=...)

CLI: python scripts/prepare_timeseries.py

검증:
- 각 .npz 의 X_seq shape [N, 12], X_profile [N, 4], y [N, 24]
- 윈도우 안에 식사 없는지 sample 5개 수동 검증
- train/val/test 의 user_ids 가 user_split.json 과 일치
- N 출력 (기대치: 환자 108명 × 1일 기준 ~3000~3500)

문제 시 보고.
```

**성공 기준**:
- [ ] 3개 .npz 생성
- [ ] shape 정확
- [ ] 식사 없는 윈도우만 추출됨
- [ ] split 무결성

**트러블슈팅**:
- 유효 윈도우 너무 적음 (<500) → stride 줄이기 (6 → 3) 또는 식사 윈도우 cutoff 90분으로
- profile scaler 인덱스 헷갈림 → 별도 `scaler["profile"]` 만들고 preprocess.py 다시 돌려서 추가

---

## P1-3: PyTorch DataLoader (두 모델 모두)

**사전 조건**: P1-1, P1-2 완료

**프롬프트**:
```
app/glucose/data_loader.py 만들어줘. 두 Dataset 클래스.

FEATURE_COLS = ["carbs", "meal_time_sin", "meal_time_cos", "current_glucose",
                "fasting_bg", "weight_kg", "activity", "diabetes_type", "meal_pattern"]
LABEL_COLS = [f"BG_{t}min" for t in range(5, 125, 5)]
CONTINUOUS_COLS = FEATURE_COLS[:6]  # 6개 (sin/cos 포함)
CATEGORICAL_COLS = FEATURE_COLS[6:]  # 3개

class MealDataset(torch.utils.data.Dataset):
- __init__(csv_path, return_categorical_separately=False)
  * X_continuous: 6컬럼 (carbs, sin, cos, current_glucose, fasting_bg, weight_kg) → float32
  * X_categorical: 3컬럼 정수 → long
  * y: 24개 BG (정규화됨) → float32
- __getitem__:
  * False: (X[9], y[24]) — 카테고리 정수 그대로 concat
  * True: ((X_cont[6], X_cat[3]), y[24])

class TimeseriesDataset(torch.utils.data.Dataset):
- __init__(npz_path)
  * np.load(npz_path) → X_seq, X_profile, y
  * 모두 텐서 변환 (X_profile 의 카테고리 부분은 long, 나머지 float)
    - 또는 단순화: 모두 float32 로 (학습 시 카테고리도 정수값 그대로 float 처리)
- __getitem__: ((X_seq[12], X_profile[4]), y[24])

함수: get_dataloader(dataset, batch_size, shuffle, num_workers=2)

테스트:
- MealDataset 두 모드 첫 배치 shape 확인
- TimeseriesDataset 첫 배치 shape: ((B, 12), (B, 4)), (B, 24)

실행 확인.
```

**성공 기준**:
- [ ] MealDataset 두 모드 동작
- [ ] TimeseriesDataset shape 정확

---

## P2-1: 모델 정의 (Model 1 3종 + Model 2 1종)

**프롬프트**:
```
app/glucose/model.py 만들어줘.

공통 상수:
N_CONTINUOUS_M1 = 6  # carbs, meal_time_sin, meal_time_cos, current_glucose, fasting_bg, weight_kg
N_CATEGORICAL_M1 = 3  # activity, diabetes_type, meal_pattern
CATEGORICAL_CARDINALITIES = (3, 3, 11)
EMBEDDING_DIM = 8
OUTPUT_DIM = 24

# Model 1 (식사 시점) ───────────────────────────────────────────────

1. MealRidge (sklearn 래퍼):
   - sklearn Ridge + MultiOutputRegressor
   - 입력 처리: 카테고리 3개를 one-hot 으로 펼침 (3+3+11 = 17) + 연속 6개 = 23-dim
   - fit(X[N, 9], y[N, 24]), predict(X) -> [N, 24]
   - alpha 후보 [0.1, 1, 10] grid search (val RMSE 최저)
   - save(path), load(path) — pickle

2. MealMLP(nn.Module):
   - __init__(embedding_dim=8, hidden=64, dropout=0.2)
   - 카테고리 3개 각각 nn.Embedding(cardinality, embedding_dim)
   - 입력: (X_cont[B, 6], X_cat[B, 3])
   - 처리: embed 3개 concat[B, 24] + X_cont[B, 6] = [B, 30] → Linear(30, 64) → ReLU → Dropout → Linear(64, 64) → ReLU → Dropout → Linear(64, 24)
   - forward(X_cont, X_cat) -> [B, 24]

3. MealLSTMDecoder(nn.Module):
   - __init__(embedding_dim=8, hidden=64, n_steps=24, dropout=0.2)
   - 카테고리 임베딩 동일
   - 인코더: Linear(30, hidden) → ReLU
   - LSTM 디코더: nn.LSTMCell(hidden, hidden), n_steps 반복
     * 첫 입력은 인코더 출력 그대로, h0/c0 = 인코더 출력 / zeros
     * 매 step hidden 으로 Linear(hidden, 1) → BG 1개
     * 다음 step 입력도 인코더 출력 (자가회귀 X)
   - forward(X_cont, X_cat) -> [B, 24]

# Model 2 (현재 시점) ──────────────────────────────────────────────

4. NowLSTM(nn.Module):
   - __init__(seq_len=12, profile_dim=4, hidden=64, n_layers=2, dropout=0.2, output_dim=24)
   - 입력: (X_seq[B, 12], X_profile[B, 4])
   - 처리:
     * X_seq → unsqueeze(-1) → [B, 12, 1]
     * X_profile → expand to [B, 12, 4] (각 timestep에 동일 profile 붙임)
     * concat → [B, 12, 5]
     * nn.LSTM(input_size=5, hidden=64, num_layers=2, batch_first=True, dropout=0.2)
     * 마지막 hidden state 사용 → Linear(hidden, 24) → [B, 24]
   - forward(X_seq, X_profile) -> [B, 24]

헬퍼:
- save_model(model, path, meta: dict): state_dict + meta JSON
- load_model(path, model_class) -> (model, meta)

테스트 (if __name__ == "__main__"):
- Model 1: X_cont[4, 6], X_cat[4, 3] → MLP, LSTMDecoder forward → [4, 24] 확인
- Model 2: X_seq[4, 12], X_profile[4, 4] → NowLSTM forward → [4, 24] 확인
- 파라미터 개수 출력
```

**성공 기준**: 4 모델 모두 더미 입력 통과, 출력 shape [B, 24]

---

## P2-2: Model 1 학습 (Ridge / MLP / LSTM)

**프롬프트**:
```
app/glucose/train_base.py 만들어줘.

# Ridge ──────────────────────────────────────────────────────────
def train_ridge(train_csv, val_csv, save_path):
    - MealDataset(return_categorical_separately=True) 으로 로드
    - X_cont [N, 6], X_cat [N, 3] → 카테고리 one-hot → concat [N, 23]
    - sklearn Ridge + MultiOutputRegressor
    - alpha [0.1, 1, 10] grid search → val RMSE 최저
    - 저장: ai/models/ridge_meal.pkl + ridge_meal_meta.json
    - 24 horizon RMSE/MAE 콘솔 출력 (30/60/90/120 강조)
    - **주의**: BG 가 정규화된 상태로 저장되어 있으므로 RMSE 계산 시 inverse_transform 후 raw mg/dL 기준으로

# Torch (MLP / LSTM) ───────────────────────────────────────────────
def train_torch_meal(model_class, train_csv, val_csv, save_path,
                    epochs=50, batch_size=64, lr=1e-3, device='cuda',
                    early_stopping_patience=5):
    - MealDataset(return_categorical_separately=True)
    - model = model_class(...)
    - MSELoss (정규화된 BG 에 대해), Adam
    - 매 epoch:
      * train: forward(X_cont, X_cat) → loss → backward
      * val: RMSE/MAE 계산. **BG inverse_transform 후 raw mg/dL 기준**
      * early stopping (val raw RMSE)
    - best 모델 저장 + meta JSON

CLI:
  python -m app.glucose.train_base --model ridge
  python -m app.glucose.train_base --model mlp
  python -m app.glucose.train_base --model lstm

실행 확인. GPU 사용 로그, 첫 epoch 시간 측정 → 전체 예상 시간 출력.
```

**성공 기준**:
- [ ] ai/models/{ridge_meal.pkl, mlp_meal.pt, lstm_meal.pt}
- [ ] val raw RMSE 30/60/120min 콘솔에 표시 (mg/dL 단위)
- [ ] GPU 사용 확인

**트러블슈팅**:
- 학습 안 떨어짐 → lr 5e-4 → 1e-4
- 데이터 적음 → dropout 0.3, batch 32
- inverse_transform 차원 안 맞음 → reshape 주의 (24 컬럼 → flatten → transform → 다시 reshape)

---

## P2-3: Model 2 학습 (NowLSTM)

**프롬프트**:
```
app/glucose/train_timeseries.py 만들어줘.

def train_now_lstm(train_npz, val_npz, save_path,
                  epochs=50, batch_size=64, lr=1e-3, device='cuda',
                  early_stopping_patience=5):
    - TimeseriesDataset(npz_path) 로드
    - DataLoader 생성
    - model = NowLSTM()
    - MSELoss (정규화 BG), Adam
    - 매 epoch:
      * train: forward(X_seq, X_profile) → loss → backward
      * val: RMSE 계산 (BG inverse_transform → raw mg/dL)
      * early stopping
    - best 모델 저장: ai/models/lstm_now.pt + lstm_now_meta.json

CLI: python -m app.glucose.train_timeseries
실행 확인.
```

**성공 기준**:
- [ ] ai/models/lstm_now.pt
- [ ] val raw RMSE 30/60/120min 콘솔 출력

**트러블슈팅**:
- 학습 데이터 윈도우 너무 적음 → P1-2 의 stride 줄이기
- 시계열 입력 분포 이상 → BG scaler 가 model1 과 동일한지 확인

---

## (선택) P2-4: 개인화 fine-tune

> 환자당 데이터 적으면 효과 제한적. 시간 남으면 진행.

**프롬프트**:
```
app/glucose/personalize.py 만들어줘.

def finetune_meal(base_model_path, train_csv_with_user, target_user_id: str,
                 save_dir, epochs=15, lr=1e-4):
    - base 모델 (lstm_meal) 로드
    - preprocess.py 가 user_id 컬럼을 메타로 저장하지만 CSV 에는 drop함
    - 옵션 A: preprocess.py 수정해서 user_id 컬럼 유지하는 변종 출력 추가
    - 옵션 B: user_split.json + meal_events.csv 로 어떤 행이 어떤 user 인지 매핑 재구성
    - target 환자 row 만 학습
    - 임베딩 freeze, 인코더+디코더만 학습 (lr base의 1/10)
    - 저장: ai/models/lstm_meal_personalized_{user_id}.pt
```

**성공 기준**: personalized 파일 생성, 해당 환자 RMSE 개선

---

## P3-1: 평가 모듈

**프롬프트**:
```
app/glucose/evaluate.py 만들어줘. EVAL_SPEC.md 정확히 따라.

LABEL_STEPS = list(range(5, 125, 5))  # 24개
KEY_HORIZONS = [30, 60, 120]  # 발표 강조

함수 1: evaluate_model(model_path, test_csv, model_type) -> dict
- 모델 로드 후 test.csv 추론
- 24 horizon별 RMSE, MAE
- Clarke EG A+B 비율 (각 horizon, 단 KEY_HORIZONS 강조)
- 피크값/피크시점 오차 (24 출력 시퀀스에서 argmax)
- dict 반환

함수 2: plot_clarke_error_grid(y_true, y_pred, horizon_min, save_path)
- EVAL_SPEC.md의 시각화 사양 정확히 따라
- Clarke 1987 영역 정의 직접 구현 (외부 라이브러리 X)
- 색상: A=#d4f4dd, B=#fff4cc, C/D/E=#ffcccc
- 산점도: 검은색, alpha=0.3
- A/B/C/D/E 영역 비율을 그림 우측 상단에 박스로 표시

함수 3: plot_predictions_curve(timestamps, y_true, y_pred, save_path)
- 시간축 라인 플롯
- y_true 검정 실선, y_pred 빨강 점선
- 신뢰구간 회색 영역 (선택)

함수 4: plot_meal_response(meal_event, true_curve, pred_curve, save_path)
- 식사 시점 표시 (수직선)
- 2시간 곡선 비교

함수 5: generate_comparison_table(results: List[dict], save_path)
- 시나리오 × 지표 표를 PNG로
- matplotlib table 활용

테스트:
- LSTM base를 sim_val로 평가 → reports/test_evaluation.png 저장
- 결과 dict 콘솔 출력
```

**성공 기준**: 
- [ ] evaluate_model 함수가 dict 반환
- [ ] Clarke EG PNG가 5개 영역 색칠된 채로 그려짐
- [ ] 영역 비율 박스 표시됨

---

## P3-2: 시나리오 자동 실행

**프롬프트**:
```
scripts/run_all_evaluations.py 만들어줘. Model 1 + Model 2 모두 평가.

scenarios_meal = [
    {"id": "M1-R", "model": "ai/models/ridge_meal.pkl", "type": "ridge", "test": "data/processed/test.csv"},
    {"id": "M1-MLP", "model": "ai/models/mlp_meal.pt", "type": "mlp", "test": "data/processed/test.csv"},
    {"id": "M1-LSTM", "model": "ai/models/lstm_meal.pt", "type": "lstm", "test": "data/processed/test.csv"},
]

scenarios_now = [
    {"id": "M2-LSTM", "model": "ai/models/lstm_now.pt", "type": "now_lstm", "test": "data/processed/timeseries/test.npz"},
]

각 시나리오:
1. evaluate_model() 호출 (raw mg/dL 기준 RMSE/MAE/CEG)
2. plot_clarke_error_grid(horizon=30, 60) → reports/scenarios/{id}_clarke_*.png
3. plot_curve_examples(n=5) → reports/scenarios/{id}_curves.png

비교 산출물:
- generate_comparison_table → reports/final_comparison_table.png
  (Model 1 의 3 모델 비교 + Model 2 의 1 모델 별도 표)
- Model 1: clarke_grid_meal_30min.png (3 모델 나란히)
- Model 2: clarke_grid_now_30min.png (단일)

CLI: python scripts/run_all_evaluations.py
```

**성공 기준**:
- [ ] reports/scenarios/ 에 4 시나리오 산출물
- [ ] reports/final_comparison_table.png
- [ ] Model 1 3개 모델 비교, Model 2 별도 평가

---

## P4-1: 추론 함수 (두 Predictor)

**프롬프트**:
```
app/glucose/predict.py 만들어줘.

# 인코딩 매핑 (DATA_SPEC.md 와 동기) ─────────────────────────────────
DIABETES_TYPE_MAP = {"T1D": 0, "T2D": 1, "Normal": 2}
ACTIVITY_MAP = {"low": 0, "medium": 1, "high": 2}
MEAL_PATTERN_MAP = {"regular_3": 0, "skip_breakfast": 1, "skip_dinner": 2,
                    "skip_lunch": 3, "skip_breakfast_dinner": 4,
                    "skip_breakfast_lunch": 5, "skip_lunch_dinner": 6,
                    "frequent_small": 7, "irregular": 8, "late_dinner": 9,
                    "fasting_day": 10}

# Model 1 (식사 시점) ───────────────────────────────────────────────
class MealPredictor:
- __init__(model_type='lstm'):
  * 모델 lazy load: ai/models/{ridge_meal.pkl, mlp_meal.pt, lstm_meal.pt}
  * scaler lazy load: ai/models/scaler.pkl (dict: model1_features, bg_target)
- _encode_features(features: dict):
  * 입력 raw: {carbs, meal_time_iso, current_glucose, fasting_bg, weight_kg,
              activity, diabetes_type, meal_pattern}
  * 카테고리 문자열 → 정수
  * meal_time_iso → hour float → sin/cos
  * scaler.model1_features.transform 적용 (carbs, current_glucose, fasting_bg, weight_kg)
  * X_cont [6], X_cat [3] 반환
- predict_curve(features: dict) -> dict:
  * forward → 24개 정규화된 BG → scaler.bg_target.inverse_transform
  * 반환: {"horizons_min": [5, 10, ..., 120], "values": [float; 24]} (raw mg/dL)
- predict_at_horizon(features: dict, horizon_min: int) -> float:
  * predict_curve 후 해당 인덱스 (horizon_min // 5 - 1)

# Model 2 (현재 시점) ──────────────────────────────────────────────
class NowPredictor:
- __init__():
  * 모델: ai/models/lstm_now.pt
  * scaler: 동일
- _encode_input(recent_values: list[float], profile: dict):
  * recent_values 길이 검증 (>=12). 부족하면 ValueError
  * 마지막 12개만 사용 (또는 복제 padding 정책)
  * scaler.bg_target.transform 적용 (시계열)
  * profile: weight_kg, fasting_bg → scaler.model1_features 의 해당 인덱스로 transform
  * activity, diabetes_type → 정수
  * X_seq [12], X_profile [4] 반환
- predict_curve(recent_values: list[float], profile: dict) -> dict:
  * forward → 24개 BG → inverse_transform → raw mg/dL
  * 반환: {"horizons_min": [5, 10, ..., 120], "values": [float; 24]}

# 모듈 레벨 lazy 싱글톤 ───────────────────────────────────────────
_meal_predictor = None
_now_predictor = None

def get_meal_predictor(model_type='lstm') -> MealPredictor: ...
def get_now_predictor() -> NowPredictor: ...

테스트:
- MealPredictor 더미 (T1D, 라면 80g, ...) → 24 곡선 모양 확인
- NowPredictor 더미 (recent_values 12개, profile dict) → 24 곡선 확인
```

**성공 기준**:
- [ ] 두 Predictor 모두 동작
- [ ] scaler 파라미터가 학습 시점과 동일하게 적용됨
- [ ] 출력이 raw mg/dL (40~400 합리적 범위)

---

## P4-2: LLM 리포트

**프롬프트**:
```
app/glucose/llm_report.py 만들어줘. anthropic SDK.

함수 1: generate_meal_comment(meal_info: dict, predicted_curve: dict) -> str
- 입력: 
  meal_info = {"name": "라면", "carbs": 80, "time": "12:30"}
  predicted_curve = {"timestamps": [+5,...,+120], "values": [110, 115, ...]}
- claude-sonnet-4-5, max_tokens=300, timeout=10s
- 시스템 프롬프트: "당뇨 환자를 위한 따뜻한 영양 코치. 2-3문장으로 친근하게 한국어로."
- 1회 retry, 실패 시 fallback ("이 식사가 혈당 변화에 영향을 줄 수 있어요.")

함수 2: generate_weekly_report(weekly_data: dict) -> dict
- 입력: avg_glucose, tir, peak_meals, hypoglycemia_count 등
- 출력 JSON 구조:
  {"summary": str, "highlights": [str, str, str], "suggestions": [str, str]}
- 응답 파싱 실패 시 fallback 구조 반환

.env에서 ANTHROPIC_API_KEY 로드.
테스트: 더미 데이터로 두 함수 호출.
```

**성공 기준**: 두 함수 호출 성공, 한국어 자연스러움

---

## P4-3: 캘리브레이션 (5분컷)

**프롬프트**:
```
app/glucose/calibration.py 만들어줘. 50줄 이하.

- fit(raw_signals: np.ndarray, ref_glucose: np.ndarray) -> dict
  * sklearn LinearRegression
  * {"slope": float, "intercept": float, "r2": float} 반환
- apply(raw_signal: float, params: dict) -> float
- save_params(params, path) / load_params(path)

테스트: 더미 데이터 (raw=glucose*0.95+noise) 로 fit + apply.
```

**성공 기준**: 50줄 이하, r2 > 0.9 (더미 기준)

---

## P4-4: Interface (api/glucose.py 가 호출)

**프롬프트**:
```
app/glucose/interface.py 만들어줘.

api/glucose.py 라우터가 import 해서 쓸 함수들 노출:

# Model 1 (식사 시점) ───────────────────────────────────────────────
def predict_meal_response(request: dict) -> dict:
    """
    입력:
    {
      "user_id": str,
      "recent_values": list[float],
      "meal": {"carbs": float, "time_iso": str},
      "user_profile": {
        "fasting_bg": float, "weight_kg": float,
        "activity": "low"|"medium"|"high",
        "diabetes_type": "T1D"|"T2D"|"Normal",
        "meal_pattern": str
      }
    }

    처리:
    - current_glucose = recent_values[-1]
    - features dict 구성 → MealPredictor.predict_curve
    - mode: "personalized" 모델 파일 있으면 그것, 아니면 "base"
    - confidence: 임시 0.85 (추후 model uncertainty)

    반환:
    {
      "horizons_min": [5, 10, ..., 120],
      "predicted": [float; 24],  # raw mg/dL
      "confidence": 0.85,
      "mode": "base" | "personalized"
    }
    """

# Model 2 (현재 시점) ──────────────────────────────────────────────
def predict_now(request: dict) -> dict:
    """
    입력:
    {
      "user_id": str,
      "recent_values": list[float],   # 최소 12개
      "user_profile": {weight_kg, fasting_bg, activity, diabetes_type}
        # meal_pattern 불필요
    }

    처리:
    - NowPredictor.predict_curve(recent_values, profile)

    반환: predict_meal_response 와 동일 schema
    """

def health_check() -> dict:
    """모델 + scaler 로드 여부, GPU 가용성"""

각 함수:
- 모델은 모듈 레벨 lazy 싱글톤 (predict.get_meal_predictor / get_now_predictor)
- ValueError 등은 raise (라우터에서 HTTPException 변환)
- docstring 입출력 예시 포함

INTERFACE.md 도 같이 만들어줘 — backend 팀에 보여줄 API 문서. 두 엔드포인트 각각 예시.
```

**성공 기준**:
- [ ] 두 함수 단독 호출 시 정상 동작 (라우터 우회 테스트)
- [ ] INTERFACE.md 작성됨 (두 엔드포인트 예시 포함)

---

## P4-5: FastAPI 라우터 (Jira 278 본체)

**프롬프트**:
```
다음 파일들 만들어줘.

(1) app/schemas/glucose.py — Pydantic 스키마 두 세트

# 공통 ──────────────────────────────────────────────────────────────
class UserProfile(BaseModel):
    fasting_bg: float
    weight_kg: float
    activity: Literal["low", "medium", "high"]
    diabetes_type: Literal["T1D", "T2D", "Normal"]

class UserProfileWithPattern(UserProfile):
    meal_pattern: Literal["regular_3", "skip_breakfast", "skip_dinner",
                          "skip_lunch", "skip_breakfast_dinner",
                          "skip_breakfast_lunch", "skip_lunch_dinner",
                          "frequent_small", "irregular", "late_dinner", "fasting_day"]

class MealInfo(BaseModel):
    carbs: float = Field(ge=0, le=300)
    time_iso: str  # ISO 8601

class PredictResponse(BaseModel):
    horizons_min: list[int]  # [5, 10, ..., 120]
    predicted: list[float]    # 24개 raw mg/dL
    confidence: float
    mode: Literal["base", "personalized"]

# Model 1 ──────────────────────────────────────────────────────────
class MealPredictRequest(BaseModel):
    user_id: str
    recent_values: list[float] = Field(min_length=1)
    meal: MealInfo
    user_profile: UserProfileWithPattern

# Model 2 ──────────────────────────────────────────────────────────
class NowPredictRequest(BaseModel):
    user_id: str
    recent_values: list[float] = Field(min_length=12)  # 최소 12개 (60분치)
    user_profile: UserProfile

(2) app/api/glucose.py — 라우터

router = APIRouter(prefix="/ai/predict/glucose", tags=["glucose"])

@router.post("/meal", response_model=PredictResponse)
async def predict_meal(req: MealPredictRequest):
    try:
        return interface.predict_meal_response(req.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception:
        logger.exception("meal prediction failed")
        raise HTTPException(status_code=500, detail="internal error")

@router.post("/now", response_model=PredictResponse)
async def predict_now(req: NowPredictRequest):
    try:
        return interface.predict_now(req.model_dump())
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception:
        logger.exception("now prediction failed")
        raise HTTPException(status_code=500, detail="internal error")

@router.get("/health")
async def health():
    return interface.health_check()

(3) app/main.py 수정 — include_router 추가

from app.api import glucose as glucose_router
app.include_router(glucose_router.router)

테스트:
- uvicorn app.main:app --reload
- /docs 에서 두 엔드포인트 보임
- /ai/predict/glucose/meal 더미 요청: T1D, 라면 80g, recent_values 단일 → 24 BG 응답
- /ai/predict/glucose/now 더미 요청: recent_values 12개, profile → 24 BG 응답
- /ai/predict/glucose/health 정상 응답
- 잘못된 요청(recent_values 11개 → /now) → 422 자동 거절
```

**성공 기준**:
- [ ] /docs 에서 두 POST + 하나 GET 보임
- [ ] 정상 요청 200 + 24개 BG 응답
- [ ] Pydantic validation 에러 422
- [ ] 내부 에러 500 + 안전한 메시지

---

## P5-1: 시연 데이터 생성

**프롬프트**:
```
scripts/generate_demo.py 만들어줘.

페르소나: "김민수, 32세, T1D 진단 6개월"

하루 시나리오 (식사 4회):
- 07:00 식빵+계란 (carbs=45)
- 12:00 라면 (carbs=80)
- 15:00 쿠키 (carbs=20)
- 19:00 비빔밥 (carbs=60)

각 식사:
1. predict_meal_response_auto (config.DEMO_CURVE_MODE 따라)
2. generate_meal_comment LLM 호출
3. 결과 dict에 저장

하루 종료:
- generate_weekly_report (1일치 요약)
- 전체를 ai/data/glucose/demo/kimminsu_day.json 으로 저장

JSON 구조:
{
  "persona": {...},
  "schedule": [...],
  "meals": [
    {
      "time": "07:00",
      "name": "식빵+계란",
      "carbs": 45,
      "baseline_glucose": 95,
      "predicted_curve": {"timestamps": [...], "values": [...]},
      "predicted_peak": {"value": 162, "time_min": 50},
      "comment": "..."
    }, ...
  ],
  "daily_summary": {
    "avg_glucose": 145,
    "tir": 0.65,
    "report": {...}
  }
}

3가지 모드(model/simulator/hybrid) 모두 한 번씩 돌려서 결과 비교 출력.
어느 모드가 가장 자연스러운지 시각화 (matplotlib 4×3 subplot).
이걸 보고 윤주가 시연 모드 결정.

실행 확인.
```

**성공 기준**:
- [ ] kimminsu_day.json 생성 (3가지 모드 각각)
- [ ] 4개 식사 모두 곡선 + 코멘트 있음
- [ ] 시각화 PNG 3가지 모드 비교 가능

---

## 마무리: git commit & 정리

**프롬프트**:
```
1. .gitignore 검토:
   - .env 무시되는지
   - app/models/glucose/*.pt, *.pkl 무시되는지 (대용량)
   - data/glucose/processed/ 무시되는지
   - reports/ 는 커밋 (발표 자료라서)

2. README.md (ai/glucose/ 모듈용) 만들어줘:
   - 설치, 데이터 준비, 학습, 평가, 추론 사용법
   - 명령어 모음

3. git status 확인 후 의미 단위로 커밋 분리 제안:
   - "feat(glucose): data pipeline"
   - "feat(glucose): LSTM base + Ridge baseline"
   - "feat(glucose): personalization + scratch comparison"
   - "feat(glucose): evaluation + Clarke EG"
   - "feat(glucose): inference + LLM report"
   - "feat(glucose): demo generation"
```

**성공 기준**: README 깔끔, 커밋 의미 단위 분리

---

## 응급 상황 대처

### 학습이 너무 오래 걸림
- epochs 절반으로
- hidden_size 32로
- early stopping patience 2로
- 일단 적당한 모델로 평가까지 끝내고, 시간 남으면 다시 학습

### 시뮬레이터 데이터 형식이 완전 다름
1. CLAUDE.md, DATA_SPEC.md 모두 업데이트
2. preprocess.py 의 컬럼/매핑만 수정
3. 다른 코드는 canonical schema에 의존하니 영향 적음

### 학습 데이터 너무 적음 (~160 train rows)
- 일단 그대로 진행해서 baseline 확보
- 시뮬레이터 팀에 데이터 추가 생성 요청 (유저당 1일 → 7일로 늘리면 7배)
- augmentation 검토 (식사 시간 ±10분 perturbation 등)

### 시연 곡선이 이상함
- config.DEMO_CURVE_MODE = 'simulator' 로 변경
- 또는 'hybrid' 로 sanity check 강화
- 미리 만든 JSON을 직접 수동 수정도 가능 (시연 안정성 우선)

### LLM API 끊김
- generate_demo.py에서 모든 코멘트 미리 생성 → JSON 캐싱
- 시연 중엔 JSON에서 읽기만
