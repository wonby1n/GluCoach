# 데스크톱 이전 실행 런북

노트북에서 검증한 실행 절차 그대로 데스크톱에서 복붙용. PowerShell 기준.

## 0. 사전 체크 — Python 종류 확인

```powershell
python --version
where.exe python
```

- 경로에 `WindowsApps\PythonSoftwareFoundation.Python` 들어있으면 **Microsoft Store Python**
- 경로가 `C:\Python313\` 또는 `C:\Users\...\AppData\Local\Programs\Python\` 이면 **python.org 정식 Python**

| 종류 | venv 사용 | 권장 |
|---|---|---|
| Store Python | ❌ 멀티프로세싱 시 `PermissionError [WinError 5] _winapi.DuplicateHandle`로 워커 죽음 | 시스템 python에 직접 설치 |
| python.org | ✅ 정상 동작 | venv 사용 권장 |

데스크톱은 가급적 python.org 정식 3.11+ 설치 후 진행. Store만 있다면 아래 시스템 설치 경로로.

---

## 1. 코드/데이터 이전

```powershell
# 노트북에서 (이미 sim_v1, sim_v2 생성됨)
# simulator/ 전체를 외장/클라우드로 복사. 단 venv/, simglucose.egg-info/ 는 제외 가능.

# 데스크톱에서 받은 후
cd C:\<your-path>\S14P31S309\ai\simulator
```

> `data/sim_v1/` (full_matrix per-cat=5, 5,940 sim) 와 `data/sim_v2/` (random_pattern per-cat=30, 3,240 sim) 는 노트북에서 생성한 자산. 보존.

---

## 2. 환경 셋업 (한 번만)

### 케이스 A — python.org 정식 Python (권장)

```powershell
python -m venv venv
venv\Scripts\activate
$env:PYTHONUTF8 = "1"
pip install "setuptools<81"
pip install -r requirements.txt
pip install -e .
```

### 케이스 B — Microsoft Store Python (venv 우회)

```powershell
# venv 생성/활성화 절대 하지 말 것
$env:PYTHONUTF8 = "1"
pip install "setuptools<81"
pip install -r requirements.txt
pip install -e .
```

### 동작 확인 (공통)

```powershell
python -c "from simglucose.dataset import enumerate_categories; print('OK', len(enumerate_categories()))"
# → "OK 108"
```

---

## 3. 데이터셋 생성

### 옵션 의미 정리 (헷갈리지 말 것)

- `--mode full_matrix`(기본): 페르소나 청사진 1개 × **식사 패턴 11개** = 11명 생성
  - 따라서 **카테고리당 사용자 = `--per-category` × 11**
- `--mode random_pattern`: 청사진 1개당 패턴 1개 무작위 = 1명 생성
  - 카테고리당 사용자 = `--per-category` × 1
- `--per-category` 는 **청사진(BP) 수**지 최종 사용자 수가 아님

### 실행 매트릭스

| 목적 | 명령 | 카테고리당 사용자 | 총 sim | 12워커 추정 |
|---|---|---|---|---|
| 스모크 | `--per-category 1 --workers 12` | 11 | 1,188 | ~5분 |
| 소규모 | `--per-category 5 --workers 12 --seed 42` | 55 | 5,940 | ~25분 |
| **본 실행 (이번 작업)** | `--per-category 30 --workers 12 --seed 44` | **330** | **35,640** | **~2.5~3시간** |
| 대규모 | `--per-category 50 --workers 12 --seed 42` | 550 | 59,400 | ~5~6시간 |

### 본 실행 명령 (PowerShell, 백그라운드 + 로그)

```powershell
$env:PYTHONUTF8 = "1"
python -u generate_dataset.py `
  --mode full_matrix `
  --per-category 30 `
  --seed 44 `
  --workers 12 `
  --output-dir data/sim_v3 `*> data/_run_v3.log
```

> `--output-dir`은 기존 `sim_v1`, `sim_v2` 안 덮도록 `sim_v3`로 분리.
> 기존 `sim_v2`(random_pattern, 3,240명)는 보존하되 다른 의미의 데이터셋이라 ML에 같이 쓰지 말 것.

### 비동기 실행 (오래 걸리는 본 실행)

PowerShell에서 background job:

```powershell
Start-Job -ScriptBlock {
  Set-Location C:\<your-path>\S14P31S309\ai\simulator
  $env:PYTHONUTF8 = "1"
  python -u generate_dataset.py --mode full_matrix --per-category 30 --seed 44 --workers 12 --output-dir data/sim_v3 *> data/_run_v3.log
} -Name simgen
```

진행 확인:

```powershell
# UTF-16 LE 로 저장되므로 그냥 type 하면 깨짐. 변환해서 보기:
[IO.File]::ReadAllText("data/_run_v3.log", [Text.Encoding]::Unicode) | Select-Object -Last 40

# 또는 git bash / WSL 에서:
#   iconv -f UTF-16LE -t UTF-8 data/_run_v3.log | tail -40

Get-Job simgen
Receive-Job simgen -Keep
```

---

## 4. 결과 검증

```powershell
# 카테고리 폴더 수 (108 이어야 함)
(Get-ChildItem data/sim_v3 -Directory).Count

# 카테고리당 사용자 행 수 (헤더 포함이라 331 이어야 함: 30 BP × 11 + 1)
(Get-Content data/sim_v3/T1D_hi_diabetes_heavy/users.csv).Count

# 총합 통계
python -c "import pandas as pd, glob; u=pd.concat([pd.read_csv(f) for f in glob.glob('data/sim_v3/*/users.csv')]); print('users:', len(u)); print('cats:', u.category.nunique()); print(u.meal_pattern.value_counts())"
```

기대값:
- 폴더 108
- 카테고리당 users.csv 331줄 (헤더 1 + 데이터 330)
- 총 users 35,640
- meal_pattern 11종 각 ~3,240명씩 균등

---

## 5. 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| `PermissionError [WinError 5] _winapi.DuplicateHandle` | Store Python venv 멀티프로세싱 | venv 비활성화하고 시스템 python 사용 |
| `setup.py` 인코딩 에러 (`cp949`) | UTF-8 강제 안 됨 | `$env:PYTHONUTF8 = "1"` 먼저 설정 |
| `ModuleNotFoundError: pkg_resources` 류 | setuptools 81+ | `pip install "setuptools<81"` |
| 로그 파일 글자 사이 공백/깨짐 | PowerShell `*>` 가 UTF-16 LE 로 씀 | 위 검증 명령으로 인코딩 변환해서 읽기 |
| 진행이 멈춘 듯 | flush 지연 또는 워커 hang | `python -u` 옵션 확인, 작업관리자에서 `python.exe` × 워커수 떠 있는지 확인 |
| `NativeCommandError` (빨간 텍스트) | pandas FutureWarning이 stderr에 찍힌 것 | 무시. exit code 0이면 성공 |

---

## 6. 노트북에서 실행한 이력 (참고)

| 실행 | 명령 요약 | 결과 |
|---|---|---|
| sim_v1 | `--mode full_matrix --per-category 5 --seed 42 --workers 8` | 5,940 users, 카테고리당 55명 |
| sim_v2 (재해석 전, 보존됨) | `--mode random_pattern --per-category 30 --seed 43 --workers 12` | 3,240 users, 카테고리당 30명, 28.8분 소요 |
| sim_v3 (이 문서의 본 실행) | `--mode full_matrix --per-category 30 --seed 44 --workers 12` | 35,640 users, 카테고리당 330명, ~2.5~3시간 예상 |
