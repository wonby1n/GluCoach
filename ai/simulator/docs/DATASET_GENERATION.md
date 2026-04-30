# 합성 데이터셋 생성 실행계획

## 목적
ML 파이프라인 콜드 스타트용 합성 CGM 데이터셋. 향후 실데이터는 같은 통합 스키마로 어댑터 합류, 본 문서는 **시뮬레이터 출력**에 집중.

타겟 ML 태스크:
1. **식전 2시간 예측** — 음식 입력 시 식후 120분 혈당 곡선
2. **CGM 10분 지연 보정** — 간질액 측정 지연 보강

---

## 시뮬레이터 변수 정리

### ODE란
시뮬레이터의 핵심은 **13개 ODE 연립 시스템** (Dalla Man 2007 / UVA-Padova). 각 식이 그 시점의 혈당·인슐린·위장 상태가 다음 5초 동안 어떻게 변할지 정의 → solver(scipy RK45)가 시간 적분 → CGM 시계열.

`kp1, kp2, Vmx, k1, k2` 같은 파라미터 = ODE 계수, 환자마다 다름. `persona_builder.py`가 페르소나(키/몸무게/공복혈당/...) → ODE 계수를 추정.

### ODE에 실제로 영향을 주는 페르소나 입력 (5개 + 조건부 1개)

| 페르소나 입력 | 카테고리(빈) 분할 | 코드 위치 |
|--------------|------------------|----------|
| `diabetes_type` | Normal / T2D / T1D — 3개 | `_pick_template_name`, `_params_path` |
| `birthdate`(나이) | T1D만: child(<14) / adolescent(14–18) / adult(≥19) — 3개 · T2D·Normal: 성인 1개 | `_pick_template_name` |
| `weight_kg` | light(40–55) / medium(55–70) / heavy(70–85) / very_heavy(85–100) — 4개 | `params["BW"] = ...` |
| `fasting_bg` | 정상(70–99) / 전당뇨(100–125) / 당뇨(126–180) / 조절불량(180+) — 타입별 차등(Normal 1, T1D·T2D 3) | `_fix_initial_glucose_state` |
| `activity` | 저(low) / 중(medium) / 고(high) — 3개 | `_apply_multipliers` |
| `medication_timing` (조건부) | 식전 / 식사 직후 / 식후 — 3개 (T2D 약 복용자만) | `_MEDICATION_TIMING_KABS` |

나머지 입력(`sex`, `height_cm`, `treatment`, `medication`, `diagnosis_years`, `hba1c`, `meal_pattern`, `target_bg`)은 informational 또는 컨트롤러·시나리오에서 소비. ODE 계수는 안 건드림. 특히 `diagnosis_years`는 의도적으로 ODE에서 끊어냈음(kp1 사건).

### 셀 매칭 4변수 vs ODE 5변수
**셀 변수 = ODE 영향 5개 − 나이**. 나이는 T1D 템플릿 분기에만 영향 → 셀 폭증 방지 위해 제외. 즉 `ODE 영향 변수 ⊃ 셀 변수`.

### T1D만 나이 분류가 중요한 이유
- **시뮬레이터 데이터 가용성**: T1D CSV는 child/adolescent/adult 3그룹 × 10명 임상 fit 제공. T2D·Normal은 성인 1그룹뿐.
- **임상적 근거**: T1D는 소아·청소년 발병이 흔하고, 사춘기 인슐린 저항성·체중당 인슐린 요구량·I:C ratio가 연령군마다 크게 다름.
- T2D는 소아 케이스 드물고, Normal은 표준 성인이면 충분.

### T2D만 약 복용 타이밍이 중요한 이유
- **T1D**: 치료=인슐린(펌프/주사). 위장 흡수 단계 없음 → `kabs` 무관.
- **Normal**: 치료=식이. 약 복용 자체가 없음.
- **T2D**: 치료=경구약(메트포르민 등). 위장에서 음식과 만남 → 복용 시점이 흡수속도 좌우.
  - 식전: 약 먼저 흡수 → 식후 spike 억제 강화 → `kabs` ×1.1
  - 식사 직후: 음식이 완충 → `kabs` ×0.85
  - 식후: 표준 ×1.0

코드 게이트: `if timing and persona.get("treatment") == "medication"`.

---

## 페르소나 카테고리 정의

[유사도 설계 문서](SIMILARITY_DESIGN.md)에서 도출한 4개 매칭 변수를 셀 정의에 사용.

| 변수 | 빈 |
|------|----|
| `diabetes_type` | T1D / T2D / Normal (3) |
| `activity` | low / medium / high (3) |
| `fasting_bg` | 타입별 차등 (아래) |
| `weight_kg` | light(40–55) / medium(55–70) / heavy(70–85) / very_heavy(85–100) (4) |

### 각 빈의 근거

| 변수 | 빈 | 근거 |
|------|----|----|
| 활동 | 저 / 중 / 고 (3) | 시뮬레이터 활동 배율 카테고리 그대로 (`persona_builder.py:43-47`) |
| 공복혈당 | 70–99 / 100–125 / 126–180 / 180+ | ADA Standards of Care 2024 §2 — 정상 / 전당뇨(IFG) / 당뇨 진단 / 조절불량 |
| 체중 | 40–55 / 55–70 / 70–85 / 85–100 (4) | 한국 성인 분포 커버. 빈 폭 15kg ≈ ±10–20% (Look AHEAD Trial NEJM 2013: 10% = clinically significant) |

### `fasting_bg` 타입별 빈 (핵심: 단순 곱셈 안 됨)

| 타입 | 허용 빈 | 근거 |
|------|--------|------|
| Normal | 정상 (1) | ADA 정의상 fbg < 100 = Normal. 그 이상은 Normal 아님 |
| T2D | 전당뇨 / 당뇨 / 조절불량 (3) | T2D는 IFG(≥100)부터 진단 시작 |
| T1D | 전당뇨 / 당뇨 / 조절불량 (3) | 동일 임상 기준 + 셀 수 균형 (T1D=T2D=36) |

T1D를 처음엔 4 빈으로 했다가 T2D와 동일한 3 빈으로 줄임 — 셀 수 불균형 해소.

---

## 최종 셀 구성

```
Normal:   3 활동 × 1 fbg × 4 체중 = 12 셀
T2D:      3       × 3      × 4    = 36 셀
T1D:      3       × 3      × 4    = 36 셀
─────────────────────────────────────────
합계:                                84 셀
```

각 셀 10명 → **총 840 페르소나**

---

## 시뮬레이션 파라미터

| 파라미터 | 값 |
|----------|----|
| 기간 | 7일 연속 |
| 시간 해상도 | 5분 (CGM 표준) |
| 시작 시각 | 자정 (00:00) |
| 식사 시나리오 | 하루 3끼 ± 30% 간식 |
| CGM 모델 | Dexcom |
| 인슐린 펌프 | Insulet (T1D/T2D만) |

PoC 측정값: ~10초/persona/day (T1D는 더 오래 — 컨트롤러 루프).

| 방식 | 예상 시간 |
|------|----------|
| 단일 프로세스 | ~21시간 |
| 4 worker | ~5시간 |
| 8 worker | ~2.5시간 |

---

## 출력 스키마 (data/simulator/)

[unified_schema.py](../simglucose/dataset/unified_schema.py)에 구현.

- **`glucose_readings.csv`**: `source, user_id, time(KST 5분), glucose(mg/dL, 노이즈 포함)`
- **`meal_events.csv`**: `source, user_id, time, carbs(g)`
- **`users.csv`**: 14개 페르소나 컬럼 + `cell` 라벨 (예: `T2D_med_diabetes_heavy`)

---

## 실행 명령

```bash
# PoC (1명/셀, 1일)
python generate_dataset.py --per-cell 1 --days 1 --seed 0

# 풀 스케일 (10명/셀, 7일)
python generate_dataset.py --per-cell 10 --days 7 --seed 42
```

---

## 의도적으로 하지 않는 것

- **저수준 ODE 파라미터(kp3, Vmx 등) 직접 샘플링** — kp1 사건 재발 방지. 페르소나 입력 레벨에서만 변동.
- **`diagnosis_years`를 셀 변수로 사용** — `_apply_diagnosis_progression` 제거됨. 메타데이터로만 기록.
- **단백·지방 식사 정보** — 음식 데이터 들어올 때까지 탄수만.
- **다일간 인슐린 감수성 변동** — Visentin 2020도 future work로 인정한 영역.

---

## 참고

- [SIMILARITY_DESIGN.md](SIMILARITY_DESIGN.md) — 4개 매칭 변수 출처
- [persona_builder.py](../simglucose/patient/persona_builder.py) — 페르소나 → ODE 계수
- [persona_sampler.py](../simglucose/dataset/persona_sampler.py) — 셀 enumerate, 샘플링
- [generate_dataset.py](../generate_dataset.py) — 오케스트레이터
