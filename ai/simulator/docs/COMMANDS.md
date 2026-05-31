# 실행 명령어 모음

데스크톱이든 노트북이든 폴더 들어가서 쓸 명령어 모음. 복붙해서 쓰면 됨.

## 0. 폴더 진입

```bash
cd 1type_simulator_clean
```

---

## 1. 최초 셋업 (한 번만)

```bash
# Python 버전 확인 (3.11 이상 필요)
python --version

# 가상환경 생성·활성화
python -m venv venv

# Windows
venv\Scripts\activate
# Mac/Linux
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt
pip install -e .

# 정상 동작 확인
python -c "from simglucose.dataset import enumerate_categories; print('OK', len(enumerate_categories()))"
# → "OK 108" 출력되면 성공
```

가상환경 끝낼 때:
```bash
deactivate
```

---

## 2. 데이터셋 생성 (메인 작업)

### 스모크 테스트 (5~10분)

```bash
python generate_dataset.py --per-category 1 --workers 4 --output-dir data/smoke
```

→ 1,188 sim. 코드/환경 동작 검증용.

### 소규모 (자기 전 끝, ~1시간)

```bash
python generate_dataset.py --per-category 5 --seed 42 --workers 8 --output-dir data/small
```

→ 5,940 sim, 패턴별 540 샘플. 첫 ML 학습용.

### 본 규모 (8 워커 ~12시간 / 16 워커 ~6시간)

```bash
# 백그라운드 실행 + 로그
python -u generate_dataset.py --per-category 50 --seed 42 --workers 8 \
  --output-dir data/simulator > data/_run.log 2>&1 &
```

진행 모니터링:
```bash
tail -f data/_run.log          # 실시간 진행
tail -1 data/_run.log          # 최근 한 줄만
ps -ef | grep generate_dataset # 프로세스 살아있는지
```

### CLI 플래그 전부

```bash
python generate_dataset.py --help

# 주요 옵션:
#   --mode {full_matrix,random_pattern}    기본 full_matrix
#   --per-category N                        청사진 수 (기본 50)
#   --seed N                                결정론 시드
#   --workers N                             병렬 워커 수
#   --output-dir PATH                       출력 폴더
#   --single-folder                         평면 CSV 1쌍 (기본은 카테고리별 폴더)
```

---

## 3. 결과 검증

### Python으로 확인

```bash
python -c "
import pandas as pd, glob
folders = glob.glob('data/simulator/*/')
print(f'카테고리 폴더: {len(folders)}')
total = sum(len(pd.read_csv(f + 'users.csv')) for f in folders[:5])
print(f'샘플 5폴더 총 user: {total}')
"
```

### 첫 폴더 확인

```bash
ls data/simulator/T1D_lo_normal_light/
head -3 data/simulator/T1D_lo_normal_light/users.csv
head -5 data/simulator/T1D_lo_normal_light/glucose_readings.csv
```

### 전체 통계

```bash
python -c "
import pandas as pd, glob
all_users = pd.concat([pd.read_csv(f) for f in glob.glob('data/simulator/*/users.csv')])
print(f'총 페르소나: {len(all_users)}')
print(f'카테고리: {all_users.category.nunique()}')
print(f'meal_pattern 분포:')
print(all_users.meal_pattern.value_counts())
"
```

---

## 4. Streamlit UI (선택)

자연어로 페르소나 분석·시뮬 돌려보고 싶을 때:

```bash
pip install streamlit  # requirements에 없으면
streamlit run app.py
```

→ 브라우저로 `http://localhost:8501` 자동 열림.

---

## 5. 진행 중인 작업 다루기

### 시뮬 죽이기

```bash
# Windows (PowerShell)
Get-Process python | Stop-Process

# Linux/Mac
pkill -f generate_dataset

# 또는 PID로
ps -ef | grep generate_dataset
kill <PID>
```

### 데이터 초기화

```bash
# 특정 출력만
rm -rf data/smoke

# 전체 재시작
rm -rf data/
```

---

## 6. 자주 쓰는 한 줄

```bash
# 카테고리 108개 목록
python -c "from simglucose.dataset import enumerate_categories, category_id; [print(category_id(c)) for c in enumerate_categories()]"

# 11개 식사 패턴
python -c "from simglucose.dataset.scenario_sampler import MEAL_PATTERNS; print(MEAL_PATTERNS)"

# 어떤 페르소나 한 명 시뮬해보기
python -c "
import numpy as np
from simglucose.dataset import enumerate_categories, sample_persona
cat = enumerate_categories()[50]
print(sample_persona(cat, np.random.default_rng(0)))
"
```

---

## 권장 워크플로 (주말 데스크톱)

```bash
# 1. 폴더 받고 셋업 (~5분)
cd 1type_simulator_clean
python -m venv venv && venv\Scripts\activate
pip install -r requirements.txt && pip install -e .

# 2. 스모크 테스트 (~5분)
python generate_dataset.py --per-category 1 --workers 4 --output-dir data/smoke
python -c "import pandas as pd, glob; print(len(glob.glob('data/smoke/*/users.csv')))"
# → 108 출력되면 OK

# 3. 본 실행 백그라운드 (~12시간 with 8 워커)
python -u generate_dataset.py --per-category 50 --seed 42 --workers 8 \
  --output-dir data/simulator > data/_run.log 2>&1 &

# 4. 진행 확인 (한 번씩)
tail -1 data/_run.log
```

---

## 시뮬 시간 추정 (실측 기반, 1일 시뮬 단위)

| BP/카테고리 | 총 sim | 4 워커 | 8 워커 | 16 워커 |
|---|---|---|---|---|
| 1 (스모크) | 1,188 | ~25분 | ~12분 | ~7분 |
| 5 (소규모) | 5,940 | ~2.5시간 | ~1.5시간 | ~50분 |
| 10 | 11,880 | ~5시간 | ~3시간 | ~1.7시간 |
| 50 (권장) | 59,400 | ~24시간 | ~15시간 | ~9시간 |

기준: sim 1개 평균 ~10초 sequential. 4 워커 wall ~1.5s/sim, 8 워커 ~0.9s/sim, 16 워커 ~0.55s/sim.
T2D는 3-구획 인슐린 ODE가 무거워 T1D 대비 ~1.5배 느림 — 후반부 약간 더 걸림.

## 출력 데이터 크기 추정

| BP/카테고리 | users 행 | glucose 행 | 디스크 용량 |
|---|---|---|---|
| 1 | 1,188 | ~344k | ~17 MB |
| 5 | 5,940 | ~1.7M | ~85 MB |
| 50 | 59,400 | ~17.2M | ~860 MB |
