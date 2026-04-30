# 1type_simulator — 합성 CGM 데이터셋 생성기

UVA-Padova T1D 시뮬레이터를 확장한 합성 CGM 데이터셋 생성 파이프라인.
T1D / T2D / Normal 세 환자 모델을 지원하며, 페르소나 카테고리 108개 ×
식사 패턴 11개의 풀 매트릭스로 결정론적 1일 시뮬을 생성한다.

## 빠른 시작

### 1. 환경 셋업 (한 번만)

```bash
# Python 3.11+ 필요
python --version

# 가상환경 권장
python -m venv venv
# Windows:
venv\Scripts\activate
# Mac/Linux:
source venv/bin/activate

# 의존성
pip install -r requirements.txt
pip install -e .

# 동작 확인
python -c "from simglucose.dataset import enumerate_categories; print('OK', len(enumerate_categories()))"
# → "OK 108" 출력되면 성공
```

### 2. 데이터셋 생성

```bash
# 스모크 테스트 (1 청사진/카테고리)
python generate_dataset.py --per-category 1 --workers 4 --output-dir data/smoke

# 본 실행 (50 청사진/카테고리 × 11 패턴 = 59,400 sim)
python generate_dataset.py --per-category 50 --seed 42 --workers 8 --output-dir data/simulator
```

출력 구조:
```
data/simulator/
  T1D_lo_normal_light/
    users.csv               (페르소나 정보)
    glucose_readings.csv    (5분 간격 CGM)
    meal_events.csv         (식사 이벤트)
  ... (108 카테고리 폴더)
```

## 디렉터리 구조

```
.
├── generate_dataset.py        # CLI 진입점
├── app.py                     # Streamlit UI (선택)
├── params/                    # 환자 cohort 파라미터 (T1D/T2D/Normal)
├── docs/                      # 문서
│   ├── CATEGORY_REFERENCE.md      # 페르소나·식사 카테고리 정의
│   ├── PARAMETER_REFERENCE.md     # ODE 파라미터 종합 레퍼런스
│   ├── DATASET_GENERATION.md      # 생성 전략 설계
│   └── SIMILARITY_DESIGN.md       # 페르소나 매핑 설계
├── simglucose/                # 코어 패키지
│   ├── dataset/                   # 데이터셋 생성 모듈 (이 프로젝트 핵심)
│   ├── patient/                   # 환자 모델 (T1D/T2D/Normal)
│   ├── simulation/                # ODE 시뮬레이션 엔진
│   ├── controller/                # 인슐린 컨트롤러
│   ├── sensor/                    # CGM 센서
│   ├── actuator/                  # 인슐린 펌프
│   └── params/                    # 센서·펌프·Quest 파라미터
├── requirements.txt
└── setup.py
```

## 핵심 개념

### 페르소나 카테고리 — 108개

```
diabetes_type × activity × fbg_bin × weight_bin
   3 (T1D/T2D/Normal) × 3 (low/med/high) × {1,4,4} (Normal은 1) × 4 = 108
```

라벨 예: `T2D_hi_diabetes_heavy`

### 식사 패턴 — 11개

`regular_3` / `irregular` / `frequent_small` / `skip_breakfast` / `skip_lunch` /
`skip_dinner` / `skip_breakfast_lunch` / `skip_breakfast_dinner` /
`skip_lunch_dinner` / `fasting_day` / `late_dinner`

자세한 내용은 [`docs/CATEGORY_REFERENCE.md`](docs/CATEGORY_REFERENCE.md).

## 시뮬 시간 추정 (실측 기반, 1일 시뮬 단위)

| BP/카테고리 | 총 sim | 4 워커 | 8 워커 | 16 워커 |
|---|---|---|---|---|
| 1 | 1,188 | ~25분 | ~12분 | ~7분 |
| 5 | 5,940 | ~2.5시간 | ~1.5시간 | ~50분 |
| 10 | 11,880 | ~5시간 | ~3시간 | ~1.7시간 |
| 50 (권장) | 59,400 | ~24시간 | ~15시간 | ~9시간 |

## 라이선스

벤더 코드 (`simglucose/`)는 원본 [jxx123/simglucose](https://github.com/jxx123/simglucose) MIT.
프로젝트 추가 코드 (`simglucose/dataset/`, `params/`, `docs/`)는 본 프로젝트 내부 자산.
