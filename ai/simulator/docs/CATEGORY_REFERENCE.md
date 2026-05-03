# 카테고리 정의 요약 — 페르소나 · 식사

본 시뮬레이터는 **두 가지 독립적인 카테고리 시스템**으로 합성 데이터를 다양화한다:

1. **페르소나 카테고리** (108개) — 사람 자체의 특성 분류
2. **meal_pattern 카테고리** (11개) — 1일치 식사 구성 분류

각각 별개의 차원이며, 한 페르소나는 두 카테고리에서 정확히 하나씩 가진다.

---

## 1. 페르소나 카테고리 — 108개

### 1.1 4차원 정의

페르소나는 4개의 임상적 축으로 분류된다 ([simglucose/dataset/persona_sampler.py](../simglucose/dataset/persona_sampler.py)):

| 축 | 값 | 개수 |
|---|---|---|
| `diabetes_type` | T1D, T2D, Normal | 3 |
| `activity` | low, medium, high | 3 |
| `fbg_bin` (공복혈당) | normal, prediabetes, diabetes, uncontrolled | 4 — **단 Normal은 `normal` 1개만 (§1.4 참조)** |
| `weight_bin` (체중) | light, medium, heavy, very_heavy | 4 |

### 1.2 카테고리 셀 수 계산

> **단순 곱은 3 × 3 × 4 × 4 = 144.** 단 Normal에서 fbg 1빈만 허용 → 36개 (3 activity × 3 빠진 fbg × 4 weight) 제외 → **실제 108**.

| 환자 유형 | activity × fbg × weight | 셀 수 |
|---|---|---|
| T1D | 3 × 4 × 4 | 48 |
| T2D | 3 × 4 × 4 | 48 |
| Normal | 3 × **1** × 4 | 12 |
| **합계** | | **108** |

> Normal이 fbg 1개만 갖는 이유: ADA 정의상 "정상인"은 공복혈당 < 100 mg/dL 단일 구간. 진단된 T1D/T2D는 치료로 어떤 범위든 들어올 수 있어 4구간 모두 허용.

### 1.3 카테고리 라벨 포맷

`{type}_{activity_short}_{fbg_bin}_{weight_bin}` — 4토큰 underscore 결합.

예시:
- `T2D_hi_diabetes_heavy` — T2D + 고활동 + 당뇨범위 fbg + 무거운 체중
- `Normal_med_normal_medium` — 정상인 + 중활동 + 정상 fbg + 중간 체중
- `T1D_lo_uncontrolled_light` — T1D + 저활동 + 미조절 fbg + 가벼운 체중

`activity_short` 매핑: `low → lo`, `medium → med`, `high → hi`.

### 1.4 fbg 빈 (mg/dL)

ADA Standards of Care 2024 기준:

| 빈 | 범위 | T1D | T2D | Normal |
|---|---|---|---|---|
| `normal` | 70 – 99 | ✓ | ✓ | **✓ (Normal은 이것만)** |
| `prediabetes` | 100 – 125 | ✓ | ✓ | ✗ |
| `diabetes` | 126 – 180 | ✓ | ✓ | ✗ |
| `uncontrolled` | 180 – 250 | ✓ | ✓ | ✗ |

> **Normal 환자**는 ADA 정의상 fbg < 100이므로 `normal` 빈 1개만 허용. "정상인이면서 fbg 200" 같은 조합은 임상적 모순.
> **T1D/T2D**는 진단 후 치료/조절 상태에 따라 4개 빈 모두 허용 (잘 조절된 환자 fbg 90 ~ 미조절 환자 fbg 230).

### 1.5 weight 빈 (kg)

한국 성인 현실 분포:

| 빈 | 범위 |
|---|---|
| `light` | 40 – 55 |
| `medium` | 55 – 70 |
| `heavy` | 70 – 85 |
| `very_heavy` | 85 – 100 |

### 1.6 카테고리 안에서의 페르소나 샘플링

같은 카테고리 셀에서 N명을 뽑으면:
- **빈 안의 연속값** (정확한 weight_kg, fasting_bg)은 균등 분포로 추출
- **성별/키/나이/진단 경과**는 자유 (cell 정의에 무관)
- **30+ ODE 파라미터**는 cohort fit (예: `adult#001`)에서 시작 → N명 모두 동일
- 페르소나가 흔드는 7~9개 ODE 파라미터만 차이남

→ 같은 셀의 다양성은 본질적으로 **BW, Gb, activity 곱셈 5개**에서만 옴.

---

## 2. meal_pattern 카테고리 — 11개

### 2.1 정의

각 페르소나는 **1일치** 식사 시나리오를 받으며, `meal_pattern` 라벨이 그날의 끼니 구성을 결정한다 ([simglucose/dataset/scenario_sampler.py](../simglucose/dataset/scenario_sampler.py)).

**강도 A 결정론적**: 라벨이 정해지면 끼니 enable/disable은 고정. 시간 jitter만 변동.

### 2.2 11개 카테고리 표

| # | 라벨 | 1일 끼니 | 시간 jitter | 임상적 의미 |
|---|---|---|---|---|
| 1 | `regular_3` | B + L + D | ±30분 | 정상 식습관 |
| 2 | `irregular` | B + L + D | ±2시간 | 시간이 들쭉날쭉 |
| 3 | `frequent_small` | B + L + D + 간식 2회 (10시·15시), 메인 mean ×0.7 | ±30분 | 자주 작게 먹음 |
| 4 | `skip_breakfast` | L + D | ±30분 | 아침 거름 |
| 5 | `skip_lunch` | B + D | ±30분 | 점심 거름 |
| 6 | `skip_dinner` | B + L | ±30분 | 저녁 거름 |
| 7 | `skip_breakfast_lunch` | D만 | ±30분 | OMAD 저녁형 |
| 8 | `skip_breakfast_dinner` | L만 | ±30분 | 낮 한 끼 |
| 9 | `skip_lunch_dinner` | B만 | ±30분 | 아침만 |
| 10 | `fasting_day` | 0끼 | — | 종일 단식 |
| 11 | `late_dinner` | B + L + D, 저녁 22:00 | ±30분 | 야식형 |

> B = breakfast (08:00), L = lunch (12:00), D = dinner (19:00, 단 `late_dinner`는 22:00)

### 2.3 탄수화물 분포 (truncated normal)

끼니별 평균/표준편차/범위 (g 단위):

| 끼니 | mean | std | 범위 |
|---|---|---|---|
| 아침 | 50 | 15 | 25 – 100 |
| 점심 | 60 | 15 | 25 – 100 |
| 저녁 | 65 | 18 | 25 – 100 |
| 간식 | 20 | 5 | 10 – 40 |

`frequent_small`은 메인 끼니의 mean에 0.7 곱셈 적용 (작게 먹음).

### 2.4 카테고리 분배

페르소나 생성 시 11개 카테고리에서 **균등 무작위** 추출 (현재 구현). 추후 임상 빈도 반영 가중 조정 가능.

---

## 3. 두 카테고리 시스템의 관계

### 3.1 users.csv에서 표현

```
user_id    category                     meal_pattern         weight_kg  fasting_bg  ...
sim_0001   T2D_hi_diabetes_heavy        skip_breakfast       85.2       160         ...
sim_0002   T2D_hi_diabetes_heavy        skip_lunch           83.1       165         ...
sim_0003   T2D_hi_diabetes_heavy        late_dinner          84.0       162         ...
```

→ **한 사람당** `category` 1개 + `meal_pattern` 1개. null 없음.

### 3.2 직교성

| | 페르소나 카테고리 | meal_pattern |
|---|---|---|
| 무엇을 분류? | 사람 (몸 상태) | 1일치 식사 구성 |
| 차원 수 | 4 (type, activity, fbg, weight) | 1 |
| 카테고리 수 | 108 | 11 |
| 층화 샘플링? | ✅ 각 셀당 N명 보장 | ❌ 셀 안에서 무작위 분배 |
| ODE 파라미터에 영향? | ✅ 직접 (BW, Gb, Vmx 등) | ❌ 시나리오만 결정 |
| users.csv 컬럼명 | `category` | `meal_pattern` |

→ 두 카테고리는 **곱하지 않고 독립**. `category`는 stratification 차원, `meal_pattern`은 페르소나 trait.

### 3.3 왜 meal_pattern은 셀 차원에 안 넣었나

만약 5차원 층화 (108×11=1188 셀)로 갔다면:
- 모든 (cell × pattern) 조합에 N명 보장
- 시뮬 비용 11배

대신 4차원 유지:
- 108 셀 그대로, meal_pattern은 셀 안에서 우연 분배
- 시뮬 비용 변화 없음
- 패턴별 통계가 부족하면 사후 weighted sampling으로 보강 가능

---

## 4. 핵심 정리

- **페르소나 카테고리 108개** = (3 type × 3 activity × {1,4,4} fbg × 4 weight). 사람의 신체적/대사적 특성으로 데이터 균형 잡기 위한 stratification.
- **meal_pattern 11개** = 1일 시뮬 안에서 끼니 구성을 결정론적으로 결정. 페르소나 trait 중 하나.
- **두 시스템은 독립** — users.csv에 별도 컬럼으로 저장.
- **시뮬 기간 1일 고정** — meal_pattern의 강도 A 결정론을 가능케 함.

---

## 5. 관련 파일

| 파일 | 역할 |
|---|---|
| [`simglucose/dataset/persona_sampler.py`](../simglucose/dataset/persona_sampler.py) | 108 카테고리 정의·열거·페르소나 추출 |
| [`simglucose/dataset/scenario_sampler.py`](../simglucose/dataset/scenario_sampler.py) | 11 meal_pattern 정의·1일 시나리오 생성 |
| [`simglucose/dataset/orchestrator.py`](../simglucose/dataset/orchestrator.py) | 카테고리 plan → 시뮬 → users.csv (`category` 컬럼 추가) |
| [`generate_dataset.py`](../generate_dataset.py) | CLI 진입점 (`--per-category` 옵션) |
| [`docs/PARAMETER_REFERENCE.md`](PARAMETER_REFERENCE.md) | 전체 ODE 파라미터·users.csv 스키마·페르소나 매핑 종합 |
