# 작업 로드맵

이 프로젝트의 진행 상황과 앞으로 할 일 정리.
대화 중 미뤘던 결정, 검토만 했던 아이디어 포함.

---

## ✅ 완료 (2026-04-28까지)

### 데이터 모델 정립

- [x] **세 환자 모델** — T1D / T2D / Normal 통합 ODE 시뮬레이터
  - vendor 코드(`simglucose`)는 T1D 전용이었던 걸 확장
  - T2D: Visentin 2020 (3-구획 인슐린, β-세포 분비)
  - Normal: Dalla Man 2007 단순화 (정상상태 자동 유도)
- [x] **페르소나 카테고리 108개** — 4차원 stratification
  - `diabetes_type × activity × fbg_bin × weight_bin`
  - Normal은 fbg 1빈만 허용 (ADA 정의 기반)
- [x] **식사 패턴 11개** — 결정론적 1일 시나리오
  - skip 조합 7개 + irregular/frequent_small/late_dinner/regular_3
- [x] **Full matrix 모드** — 페르소나 × 11 패턴 풀 곱
  - `persona_blueprint_id`로 같은 청사진 11행 식별
  - 카테고리별 폴더 출력
- [x] **users.csv 필터링 컬럼** — `fbg_bin`, `weight_bin`
  - parsing 없이 단일 등호 비교로 필터 가능
- [x] **glucose 정밀도 표준화** — float64 raw → DECIMAL(5,2) (소수 2자리)

### 파이프라인 인프라

- [x] **층화 페르소나 샘플러** ([persona_sampler.py](../simglucose/dataset/persona_sampler.py))
- [x] **결정론적 시나리오 샘플러** ([scenario_sampler.py](../simglucose/dataset/scenario_sampler.py))
- [x] **시뮬레이션 러너** ([simulator_runner.py](../simglucose/dataset/simulator_runner.py))
- [x] **통합 스키마 변환기** ([unified_schema.py](../simglucose/dataset/unified_schema.py))
- [x] **풀 매트릭스 오케스트레이터** ([orchestrator.py](../simglucose/dataset/orchestrator.py))
- [x] **CLI 진입점** ([generate_dataset.py](../generate_dataset.py))
- [x] **Streamlit UI** ([app.py](../app.py))
- [x] **multiprocessing 병렬화** — 결정론 보장된 워커 풀

### 문서

- [x] [PARAMETER_REFERENCE.md](PARAMETER_REFERENCE.md) — ODE 파라미터 종합
- [x] [CATEGORY_REFERENCE.md](CATEGORY_REFERENCE.md) — 페르소나·식사 카테고리
- [x] [DATASET_GENERATION.md](DATASET_GENERATION.md) — 생성 전략 설계
- [x] [SIMILARITY_DESIGN.md](SIMILARITY_DESIGN.md) — 페르소나 매핑 설계
- [x] [COMMANDS.md](COMMANDS.md) — 실행 명령어 모음

### 정리

- [x] 코드 리네임: `cell` → `category` (8 파일)
- [x] vendor 분리: `simglucose/params/` → 프로젝트 루트 `params/`
- [x] 경로 중앙화: `simglucose/_paths.py`
- [x] 깔끔한 새 폴더 (`1type_simulator_clean/`) 분리

---

## 🟡 데이터 생성 단계별 계획

**최종 목표: 카테고리당 50 BP × 11 패턴 = 59,400 sim**.
한 번에 24시간짜리 작업 강행 안 하고 **소규모 → 중규모 → 본 규모로 점진 확장**.
각 단계마다 다음 단계 진입 트리거 명시.

### Phase 0 — 환경 이전 (이번 주)

- [ ] 노트북 → 데스크톱
  - zip 압축 (272 KB) → 메신저 → 데스크톱 unzip
- [ ] 데스크톱 환경 셋업 (~5분)
  - Python 3.11+ 확인
  - `python -m venv venv && venv\Scripts\activate`
  - `pip install -r requirements.txt && pip install -e .`

### Phase 1 — 스모크 테스트 (1 BP/cat, ~5~10분)

```bash
python generate_dataset.py --per-category 1 --workers 4 --output-dir data/smoke
```

- 총 sim: **1,188** (108 카테고리 × 1 BP × 11 패턴)
- 목적: 환경 정상 동작·임포트 검증
- 출력 검증: 108 폴더 생성, 각 폴더 11 user 행
- **다음 단계 진입 조건**: 모든 폴더 생성 + `meal_pattern` 11종 등장 + 실패 0건

### Phase 2 — 첫 ML 학습용 (5 BP/cat, ~25~50분)

```bash
python generate_dataset.py --per-category 5 --seed 42 --workers 8 --output-dir data/sim_v1
```

- 총 sim: **5,940** (108 × 5 × 11)
- 패턴별 샘플: 540
- 데이터 용량: ~85 MB
- 목적: **baseline ML 모델 학습**
- **다음 단계 진입 조건**:
  - baseline 모델 학습 가능 (input/output shape 검증)
  - 학습 곡선이 "제대로 학습되는 추세" 확인
  - 카테고리·패턴 통계 의미 있는 신호 (variance > noise floor)

### Phase 3 — 중규모 (10~20 BP/cat, ~50분~3시간)

```bash
# 10 BP — 1.7시간 with 4 workers / 50분 with 8 workers
python generate_dataset.py --per-category 10 --seed 42 --workers 8 --output-dir data/sim_v2

# 20 BP — 5시간 with 8 workers
python generate_dataset.py --per-category 20 --seed 42 --workers 8 --output-dir data/sim_v3
```

- **다음 단계 진입 조건** (둘 다 충족 시 50 BP로):
  - Phase 2 baseline에서 **연속값 분포(체중·fbg) 일반화 부족** 확인
  - 또는 (cat × pattern) 조합 통계가 5개로 부족하다는 분석 결과

### Phase 4 — 본 규모 (50 BP/cat, ~6~12시간) — **최종 목표**

```bash
# 8 워커 ~12시간
python -u generate_dataset.py --per-category 50 --seed 42 --workers 8 \
  --output-dir data/sim_final > data/_run.log 2>&1 &

# 16 워커면 ~6시간
python -u generate_dataset.py --per-category 50 --seed 42 --workers 16 \
  --output-dir data/sim_final > data/_run.log 2>&1 &
```

- 총 sim: **59,400**
- 패턴별 샘플: 5,400
- 데이터 용량: ~860 MB
- 목적: 최종 ML 학습·검증·모델 출시
- **트리거**: Phase 2 또는 Phase 3에서 "더 데이터 필요"로 결론났을 때

### 단계별 비교 (실측 기반)

| Phase | BP/cat | 총 sim | 4 worker | 8 worker | 16 worker | 데이터 | 용도 |
|---|---|---|---|---|---|---|---|
| 1 | 1 | 1,188 | ~25분 | ~12분 | ~7분 | 17 MB | 환경 검증 |
| 2 | 5 | 5,940 | ~2.5시간 | ~1.5시간 | ~50분 | 85 MB | 첫 ML 학습 |
| 3a | 10 | 11,880 | ~5시간 | ~3시간 | ~1.7시간 | 170 MB | 학습 데이터 보강 |
| 3b | 20 | 23,760 | ~10시간 | ~6시간 | ~3.5시간 | 340 MB | 분포 다양성 강화 |
| **4** | **50** | **59,400** | **~24시간** | **~15시간** | **~9시간** | **860 MB** | **최종 목표** |

> 기준: 평균 sim 1개 = 약 10초 sequential. 실효 wall time 4 워커 ~1.5초/sim, 8 워커 ~0.9초/sim, 16 워커 ~0.55초/sim (병렬 효율 약 1.7~3배).
> T2D는 3-구획 인슐린 ODE가 무거워 T1D 대비 ~1.5배 더 걸림.

### 단계 건너뛰기?

- **돈·시간이 많고 본 규모 자신 있으면** Phase 1 → Phase 4 직행 가능
- **단 Phase 2 안 거치면 baseline 모델 학습이 막혔을 때 데이터/모델 어느 쪽 문제인지 분리 어려움** — 추천 안 함
- **Phase 1은 무조건** (스모크 5분이면 끝)

---

## 🔵 다음 단계 (데이터 완성 후)

### ML 학습 단계

- [ ] **Baseline 모델 학습**
  - 입력: 페르소나 attrs + 과거 BG + 식사 이벤트
  - 출력: 다음 N분 BG 예측
  - 후보 아키텍처: LSTM, Transformer, Temporal Fusion
- [ ] **카테고리별 성능 평가**
  - T1D vs T2D vs Normal 정확도 차이
  - 어떤 meal_pattern이 가장 어려운지
- [ ] **Robustness 테스트**
  - 학습 안 본 페르소나(같은 카테고리, 다른 BP)에서 일반화?
  - meal_pattern 일반화 (학습 안 본 패턴)?

---

## ⚪ 백로그 (검토했지만 미뤘음)

### 데이터 차원 확장

- [ ] **light/heavy eater 차원**
  - 페르소나 trait으로 식사량 시프트 (mean × 0.7~1.3)
  - meal_pattern과 직교
  - **트리거**: baseline 학습 결과 식사량 일반화 부족 시
- [ ] **BMI 기반 셀 (weight 대신)**
  - 임상적으로 BMI가 BW보다 의미 있음
  - 단 ODE는 BW만 받아 변환 필요
  - **트리거**: 임상 도메인 전문가 피드백 받을 때
- [ ] **알약 + 인슐린 병용 (T2D 후기)**
  - 현재 `treatment` 컬럼 단일값(`insulin`/`medication`/`diet`)
  - 후기 T2D 환자는 메트포르민 + 인슐린 병용
  - 페르소나 스키마 + 시뮬 경로 분기 필요
  - **트리거**: 병용 케이스 ML 학습 가치 입증되면

### 카테고리 확장

- [ ] **meal_pattern을 셀 차원에 추가** (108 × 11 = 1,188 셀)
  - 모든 (cat × pattern) 조합 N명 보장
  - 시뮬 비용 11배
  - **트리거**: 분석에서 특정 (cat × pattern) 통계가 부족하다 드러나면
- [ ] **시간 차원 추가** (현재 1일만)
  - cross-day 효과 (전날 저녁 → 다음날 아침 BG)
  - 7일/30일 시뮬 옵션
  - **트리거**: 시계열 모델(LSTM)에 cross-day가 필요하다 입증되면

### 시뮬 컬럼 추가

- [ ] **meal_events.csv에 meal_type 라벨 (B/L/D/snack)**
  - 작업 시작했다가 중단 (탄수 카테고리 논의로 빠짐)
  - scenario_sampler가 (time, carbs, meal_type) 3-tuple 반환하게 수정
- [ ] **glucose_readings.csv에 slope 컬럼**
  - 혈당 변화율 (mg/dL/min)
  - DECIMAL(3,1), 5분 윈도우 기준
  - 트렌드 화살표(↑↑/↓ 등) 등급 산출 가능

### 시뮬러 자체

- [ ] **체크포인팅 / resume**
  - 현재 본 규모 24h 중간에 죽으면 전부 손실
  - 카테고리 단위로 중간 저장 + 재시작 시 skip
  - **트리거**: 본 규모 재실행이 잦아질 때
- [ ] **Numba JIT 최적화**
  - ODE 함수에 `@jit` — 5~10배 가속
  - 작업 ~반나절
  - **트리거**: 데이터 자주 재생성 (디자인 변경 잦음) 시
- [ ] **JAX/diffrax GPU 이식**
  - cross-sim 배치로 GPU 한 번에 풀기 → 50~100배 가속
  - 작업 2~3일
  - **트리거**: 데이터셋 1주 단위로 재생성 필요해질 때
- [ ] **ODE step 1분 → 5분 옵션**
  - 정밀도 일부 손실하고 5배 가속
  - **트리거**: 시뮬 비용이 ML 학습 비용 압도할 때

### 문서·인프라

- [ ] **단위 테스트 작성** — `tests/`는 vendor 테스트라 우리 코드 미커버
  - sample_scenario 11 카테고리 끼니 수 검증
  - persona_to_category 라운드트립
- [ ] **Parquet 출력 옵션** — CSV 대비 10배 압축, 타입 보존
  - 본 규모 데이터 ~860MB → ~80MB
  - **트리거**: ML 파이프라인이 Parquet 선호일 때
- [ ] **Git 정상화 + private repo**
  - 현재 origin은 vendor jxx123/simglucose
  - 우리 fork에 push 또는 새 origin 추가

---

## 📅 추정 일정 (낙관적)

| 마일스톤 | 예상 |
|---|---|
| 데스크톱 이전·셋업 | 4월 30일 |
| 본 규모 데이터 생성 완료 | 5월 첫 주말 |
| Baseline ML 모델 학습 | 5월 둘째 주 |
| 결과 평가·다음 단계 결정 | 5월 셋째 주 |
| (필요 시) 백로그에서 1~2개 픽업 | 5월 넷째 주~ |

---

## 의사결정 원칙

새 기능을 백로그에서 active로 옮기기 전 점검:

1. **트리거 조건** 명시 — "왜 지금 이걸 해야 하나?" 답할 수 있어야
2. **블록 여부** — ML 학습 진행을 막는가? 아니면 nice-to-have?
3. **반복 비용** — 한 번 만들면 끝인가, 디자인 바뀔 때마다 다시 해야 하나?
4. **데이터 손실 위험** — 기존 데이터 호환성 깨지면 재생성 시간 비용

이 4개 통과해야 우선순위 부여.
