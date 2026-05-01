# AI 서버 인수인계 문서

> 작성일: 2026-05-01
> 대상: 이후 AI 파트 담당자

---

## 1. 프로젝트 개요

비침습 CGM 패치(화이바이오메드)를 위한 AI 백엔드 서버.
패치 원시 신호 → 혈당(mg/dL) 변환은 백엔드/앱 담당. AI 서버는 BG 값을 입력으로 받음.

**두 가지 예측 기능:**
| 모델 | 엔드포인트 | 입력 | 출력 |
|------|-----------|------|------|
| Model 1 (식사 시점) | `POST /api/predict/glucose/meal` | 탄수화물, 식사 시각, 사용자 프로파일 | 식후 5~120분 BG 24개 |
| Model 2 (현재 시점) | `POST /api/predict/glucose/now` | 최근 60분 BG 12개, 사용자 프로파일 | 향후 5~120분 BG 24개 |

---

## 2. 코드 구조

```
ai/
├── app/
│   ├── main.py               FastAPI 엔트리포인트, 라우터 등록
│   ├── api/glucose.py        4개 HTTP 엔드포인트 정의
│   ├── schemas/glucose.py    요청/응답 Pydantic 스키마
│   └── glucose/
│       ├── constants.py      피처명, 인코딩 매핑, 경로 상수
│       ├── model.py          MealRidge / MealMLP / MealLSTMDecoder / NowLSTM
│       ├── predict.py        추론 로직, user별 predictor 캐시
│       ├── interface.py      API 레이어 → predict.py 호출, dummy fallback
│       ├── personalize.py    유저별 fine-tuning, 자동 폐기 로직
│       ├── data_loader.py    MealDataset, TimeseriesDataset
│       ├── train_base.py     Model 1 학습 (Ridge/MLP/LSTM)
│       ├── train_timeseries.py Model 2 학습 (NowLSTM)
│       ├── evaluate.py       RMSE/MAE/Clarke EG 평가, 시각화
│       ├── llm_report.py     Claude API 식사 코멘트/주간 리포트
│       └── seed.py           재현성용 랜덤 시드
├── scripts/
│   ├── preprocess.py         원본 3-file CSV → Model 1 학습 데이터 + scaler
│   ├── prepare_timeseries.py 슬라이딩 윈도우 → Model 2 학습 데이터
│   ├── train.py              학습 CLI 진입점
│   └── run_all_evaluations.py 전체 모델 배치 평가
├── models/                   학습된 가중치 (gitignore, 서버에 직접 보관)
├── data/processed/           전처리 결과 (gitignore)
├── docs/                     본 문서 폴더
└── glucose_docs/             상세 설계 문서 (DATA_SPEC, EVAL_SPEC, INTERFACE 등)
```

---

## 3. 개인화 흐름

```
[백엔드] 유저 14일 경과 감지
    ↓
POST /api/predict/glucose/personalize
    ↓
personalize.py: base 모델 로드 → 유저 history로 fine-tuning (15 epoch)
    ↓
새 모델 RMSE@30min < base RMSE@30min?
    ├── YES → models/lstm_meal_personalized_{user_id}.pt 저장
    │         응답: status="personalized"
    │         이후 /meal 호출 시 자동으로 개인화 모델 사용 (mode="personalized")
    └── NO  → 저장 안 함 (기존 개인화 모델 있으면 유지)
              응답: status="rejected"
              이후 /meal 호출 시 기존 개인화 또는 베이스 모델 사용
```

**중요**: rejected 후 재시도 정책은 백엔드 팀 재량. 현재 미정의 (CROSS_TEAM_DISCUSSIONS.md 참고).

---

## 4. 모델 파일 관리

- 위치: `ai/models/` (서버 로컬, gitignore)
- 파일: `lstm_meal.pt`, `lstm_now.pt`, `scaler.pkl`, `ridge_meal.pkl`, `mlp_meal.pt`
- 유저 개인화: `lstm_meal_personalized_{user_id}.pt` — 90일 미사용 시 자동 삭제됨
- 배포 시 모델 파일을 서버에 직접 복사해야 함 (S3 같은 공유 스토리지 미사용)

**재학습이 필요하면:**
```bash
cd ai/
python scripts/preprocess.py          # Model 1 데이터
python scripts/prepare_timeseries.py  # Model 2 데이터
python scripts/train.py --model lstm  # Model 1 LSTM 학습
python -m app.glucose.train_timeseries  # Model 2 학습
```

---

## 5. 알려진 한계 및 기술 부채

### 5-1. 재개인화 시 검증 데이터 중복
재개인화 호출 시 history 전체를 70/30으로 나눠 검증함.
이전 개인화에 쓰인 데이터가 재사용되므로 진짜 "새 데이터 기반 검증"이 아님.
개선 방법: 마지막 개인화 타임스탬프를 저장하고, 이후 새로 들어온 식사만 검증 셋으로 사용.

### 5-2. 개인화 모델 서버 로컬 저장
모델이 서버 로컬 디스크에 저장되므로, 서버 증설 시 각 서버 간 모델 불일치 발생.
개선 방법: S3 등 공유 스토리지로 이전.

### 5-3. 개인화 학습이 동기(blocking) 처리
`/personalize` 요청 중 학습(15 epoch)이 진행되는 동안 별도 스레드에서 실행되나,
완전한 비동기(Celery + Redis)는 미구현. 동시 개인화 요청 다수 시 스레드풀 포화 가능.

### 5-4. 시뮬레이터 데이터만 사용
실제 임상 데이터 없이 simglucose 기반 시뮬레이터 데이터로만 학습됨.
실제 환자 데이터로 재학습 시 전처리 스키마(DATA_SPEC.md) 확인 후 진행.

### 5-5. 노이즈 필터링 프론트 단독
BLE raw signal → BG 변환 및 이상값 필터링이 Android 앱에서만 수행됨.
백엔드 수신 시 범위 검증 미구현. (CROSS_TEAM_DISCUSSIONS.md 참고)

---

## 6. 환경 설정

```bash
# 의존성
pip install -r requirements.txt
pip install -r requirements_glucose.txt

# 환경변수 (.env)
ANTHROPIC_API_KEY=...   # LLM 리포트 기능 사용 시 필요
                        # 없어도 서버 기동은 됨 (fallback 메시지 반환)

# 실행
uvicorn app.main:app --host 0.0.0.0 --port 8000

# 환경 검증
python scripts/check_env.py
```

---

## 7. 주요 설계 결정 및 이유

| 결정 | 이유 |
|------|------|
| MLflow 대신 .meta.json | 인프라 없이 가볍게 실험 메타데이터 관리. 팀 규모 대비 MLflow 운영 비용 과다 |
| Model 1 / Model 2 분리 | 식사 시점(이벤트 기반)과 현재 시점(시계열 기반)은 입력 구조가 달라 단일 모델로 통합 불가 |
| dummy fallback | 모델 파일 없어도 API 응답 가능하도록 → 백엔드 통합 테스트를 학습 전에 진행 가능 |
| 개인화 자동 폐기 | 개선 없는 모델 배포 방지. 유저 손해 없이 안전한 개인화 |
| 재개인화 시 기존 모델 유지 | rejected 시 기존 개인화 모델을 삭제하지 않음 — 재시도 실패가 기존 성과를 없애면 안 됨 |

---

## 8. 참고 문서

| 문서 | 위치 | 내용 |
|------|------|------|
| API 스펙 | `glucose_docs/INTERFACE.md` | 엔드포인트 요청/응답 상세 |
| 데이터 스키마 | `glucose_docs/DATA_SPEC.md` | 학습 데이터 컬럼 정의 |
| 평가 방법 | `glucose_docs/EVAL_SPEC.md` | RMSE/Clarke EG 기준 |
| 개발 원칙 | `glucose_docs/CLAUDE.md` | 설계 결정 이력 |
| 타팀 논의 사항 | `docs/CROSS_TEAM_DISCUSSIONS.md` | 미결 협의 항목 |
