# 사용자 유사도 기준 설계 문서

## 1. 목적과 적용 범위

### 배경
GlucoCoach는 사용자가 14개 페르소나 파라미터(자연어 또는 직접 입력)로 회원가입하면, 시뮬레이터로 생성·축적된 데이터를 활용해 **개인화된 추천**을 제공하는 시스템이다.

개인화의 핵심 전제는 *"이 사용자와 비슷한 사람들의 데이터를 활용한다"* 인데, 이 문서는 그 **"비슷함"을 어떻게 정의할 것인가**에 대한 설계를 다룬다.

### 적용 범위
- **포함**: 시뮬레이션 결과 기반 매칭 기준, 각 기준의 임계값과 근거
- **제외**: 매칭 알고리즘 구현 코드, 추천 로직, 실제 SQL/벡터 검색 설계

---

## 2. 사전 분석: 14개 파라미터 중 ODE에 영향을 주는 건 5개

[`build_params_from_persona()`](../simglucose/patient/persona_builder.py) 분석 결과, 14개 페르소나 입력 중 **시뮬레이션 결과를 실제로 변화시키는 파라미터는 5개**이다.

| 파라미터 | 시뮬레이션 영향 | 비고 |
|----------|----------------|------|
| `diabetes_type` | 환자 모델 자체가 달라짐 (t1d.py / t2d.py / normal.py) | 매칭 핵심 |
| `weight_kg` | BW 직접 오버라이드 | |
| `fasting_bg` (또는 `hba1c`) | Gb 직접 오버라이드 | 가장 민감 |
| `activity` | Vmx, p2u, kabs 배율 | 이산 3단계 |
| `medication_timing` | kabs 추가 배율 | medication 치료군에서만 |

**ODE에 영향이 없는 9개** (`sex`, `height_cm`, `treatment` 라벨, `medication` 이름, `diagnosis_years`, `meal_pattern`, `target_bg`, 그 외 정보성 항목)는 **유사도 기준에서 제외**한다. 시뮬레이션 결과가 동일한데 이런 값으로 매칭을 거르면 데이터를 무의미하게 희소화시킨다.

> `diagnosis_years`는 원래 `kp1` 감쇠로 ODE에 영향을 주는 코드가 있었으나, 그 진행 모델이 검증되지 않은 가정에 기반하고 있어 §5의 결정에 따라 제거됐다. 현재는 메타데이터로만 수집된다.

> 키 1cm 차이가 무의미하다는 직관이 ODE 분석으로 확인된다 — `height_cm`은 BMI 표시용일 뿐 ODE 입력이 아니다.

---

## 3. 유사도 기준 4개

`medication_timing`은 medication 치료군에서만 의미가 있어 일반적인 유사도 기준에는 포함하지 않고, 나머지 4개를 핵심 기준으로 사용한다.

### 3.1 `diabetes_type` — 완전 일치 (Hard Filter)

**기준**: T1D ↔ T2D ↔ Normal은 절대 혼합 불가.

**근거**:
- 임상: ICD-10에서 T1D(E10)와 T2D(E11)는 별개 질환 (자가면역 vs 인슐린 저항성)
- 코드: 각 타입마다 별도 ODE 모델 사용 (`simglucose/patient/{t1d,t2d,normal}.py`). 모델이 다르면 시뮬레이션 결과의 단위·스케일 자체가 달라 비교 불가능

### 3.2 `activity` — 인접 카테고리 허용

**기준**: Vmx 배율 차이가 50% 이하면 같은 그룹.

**근거** ([`persona_builder.py:43-47`](../simglucose/patient/persona_builder.py#L43-L47)의 실제 배율표):

```python
"low":    {"Vmx": 0.7, "p2u": 0.8, "kabs": 0.9}
"medium": {"Vmx": 1.0, "p2u": 1.0, "kabs": 1.0}
"high":   {"Vmx": 1.5, "p2u": 1.2, "kabs": 1.1}
```

| 비교 쌍 | Vmx 비율 차 | 같은 그룹 |
|---------|-------------|-----------|
| low ↔ medium | 30% | ✅ |
| medium ↔ high | 50% | ✅ (경계선) |
| low ↔ high | 80% | ❌ |

→ 결과적으로 "옆 칸"끼리는 허용, "한 칸 건너"는 제외.

### 3.3 `fasting_bg` — ADA 진단 구간 동일

**기준**: 같은 임상 구간 안에 있으면 동일 그룹.

| 공복혈당 (mg/dL) | 임상 구간 |
|------------------|-----------|
| 70–99 | 정상 (Normoglycemia) |
| 100–125 | 전당뇨 (Impaired Fasting Glucose) |
| 126–180 | 당뇨 진단 + ADA 목표 외 (>130) |
| 180+ | 식후 고혈당 임계 초과 (조절 불량) |

**근거**:
- ADA Standards of Care 2024 §2 (Diagnosis) — 진단 임계값 그대로 사용
- ODE 민감도: Gb를 직접 덮어쓰기 때문에 약 30 mg/dL 단위로 시뮬레이션 결과가 유의미하게 달라지며, 이는 ADA 임상 구간폭(25–30 mg/dL)과 일치

### 3.4 `weight_kg` — 4개 고정 빈

**기준 (코호트 생성용)**: 다음 4개 고정 빈 중 같은 빈 안에 있으면 동일 그룹.

| 빈 이름 | 범위 (kg) |
|---------|-----------|
| `light` | 40–55 |
| `medium` | 55–70 |
| `heavy` | 70–85 |
| `very_heavy` | 85–100 |

**근거**:
- 한국 성인 인구의 실제 체중 분포(여 ~50–70kg, 남 ~60–90kg)를 4개 빈으로 나눔
- 각 빈 폭 15kg ≈ 평균 체중의 ±10–20% 수준 → Look AHEAD Trial(NEJM 2013) "10% = clinically significant" 임계값과 정합
- ODE: BW가 분포 용적(Vg, Vi)에 선형 영향. 빈 폭 안의 차이는 시뮬레이션 결과에 미미

**매칭 시 사용법**: 사용자의 실제 weight_kg가 어느 빈에 속하는지 보고 그 빈 + 인접 빈을 후보로 — 실제 매칭 임계는 §4 단계적 완화에서 결정.

---

## 4. 매칭 전략: 단계적 완화

### 기본 룰: 4/4 AND 매칭

```
1. diabetes_type 일치?      → NO면 탈락
2. activity 인접?           → NO면 탈락
3. fasting_bg 같은 구간?    → NO면 탈락
4. weight_kg ±10%?          → NO면 탈락
```

4개 모두 통과한 사용자만 "비슷한 집단"으로 본다.

### 매칭 풀 부족 시: 단계적 완화

4/4 매칭이 N명 미만이면, **임상적으로 덜 핵심적인 기준부터 순서대로** 완화한다:

```
완화 순서: weight_kg → activity
끝까지 유지: diabetes_type, fasting_bg
```

**이 순서의 근거**:
- `diabetes_type`: 모델 자체가 다르므로 절대 불가
- `fasting_bg`: 매일의 혈당 동역학을 가장 직접적으로 좌우하는 임상 핵심 변수
- `activity`: 카테고리 변수라 한 단계 풀면 사실상 전체 집단이 됨 → 늦게 완화
- `weight_kg`: ±10%를 ±20%로 완화하는 식의 점진적 완화 가능 → 첫 완화 대상

### 권장 임계 인원
- 충분: 30명 이상 → 4/4 그대로 사용
- 보통: 10–30명 → weight_kg 임계 완화 (±10% → ±15%)
- 부족: 10명 미만 → weight_kg + activity 완화

(실제 임계 인원은 데이터 축적 후 통계로 재조정 필요 — §6 부록 참고)

---

## 5. 결정 기록: `_apply_diagnosis_progression` 제거 및 `diagnosis_years` 제외

### 5.1 제거된 코드

이전 버전의 [`persona_builder.py`](../simglucose/patient/persona_builder.py)에는 다음 함수가 존재했다:

```python
def _apply_diagnosis_progression(params, diagnosis_years, patient_type):
    if patient_type == "Normal" or not diagnosis_years:
        return
    decay = min(1.0, float(diagnosis_years) / 30.0)
    if "kp1" in params.index:
        params["kp1"] = float(params["kp1"]) * (1.0 - 0.3 * decay)
```

이 함수와 호출부는 **본 설계 작업의 일환으로 제거됐다**. 이유는 아래와 같다.

### 5.2 제거 이유 3가지

**1) "1%/year × 30년" 수치가 어떤 논문에도 없음**
- Dalla Man 2007 (Meal Simulation Model) — 시간에 따른 파라미터 변화 모델 없음
- Visentin 2020 (Padova T2D Simulator) — 진단 기간 진행 모델 없음. Limitations에 *"future model refinement will include intra-/interday variability"*로 미해결 영역임을 명시
- 코드에 출처 주석도 없음 → 작성자의 임의 휴리스틱으로 보임

**2) `kp1`은 췌장이 아니라 간 파라미터**
- Dalla Man 2007 정의: *"k_p1 is the extrapolated EGP at zero glucose and insulin, **liver glucose effectiveness**"*
- "당뇨 진행 = 췌장 기능 소실"을 모델링하려면 β-cell 파라미터(Dalla Man 2007의 K, α, β / Visentin 2020의 Φs, Φd)를 줄여야 함
- 제거된 코드는 잘못된 변수를 건드리고 있었음

**3) 변경 방향이 임상과 반대**
- `kp1`을 줄이면 → 간이 포도당을 덜 만든다 → 혈당이 *낮아*진다
- 임상 진행 = 혈당 조절 *악화* → 제거된 코드는 진단 기간이 길수록 환자를 *덜* 당뇨병스럽게 만들었음
- T1D의 경우 더 심각: T1D 모델은 `kp1`이 크고 `kp2`도 함께 크게 잡혀 있어 균형이 맞춰져 있는데, `kp1`만 줄이면 steady-state 균형이 깨져 음수 EGP 같은 비현실적 상태가 발생할 수 있음

**참고: 원본 UVA/Padova 시뮬레이터 자체는 정확하다.** 문제는 그 위에 얹혔던 `_apply_diagnosis_progression` 커스텀 코드였다. CSV 템플릿의 기본 kp1 값(T1D adolescent#001 = 11.50, T2D t2d#001 = 2.37, Normal adult = 2.7)은 원본 모델의 정상상태(steady-state) 제약을 만족하는 검증된 값이며, 이제 그 값들이 변형 없이 사용된다.

### 5.3 더 나은 대안을 만들지 않은 이유

"`kp1` 대신 `kp3`/`Vmx`를 줄이는 식으로 다시 짜면 되지 않느냐"는 질문이 가능하다. 그러나:

- UKPDS는 임상 지표(HOMA-B, FPG, HbA1c)의 시간 변화만 다룸
- Dalla Man 모델 파라미터(`kp3`, `Vmx`)에 대한 종단 연구는 사실상 부재
- 어떤 비율로 어느 파라미터를 줄여야 하는지에 대한 직접 근거가 없음

→ "1%/year"를 "4%/year"로 바꿔도 똑같이 임의의 휴리스틱일 뿐이다. **잘못된 모델보다 모델이 없는 게 낫다**는 원칙에 따라 진행 모델 자체를 두지 않기로 결정.

### 5.4 `diagnosis_years`의 현재 위상

- 페르소나 입력에서는 여전히 수집 (UI 표시, 사용자 자기인식 도구로서 가치)
- ODE 시뮬레이션에는 영향 없음
- **유사도 매칭 기준에서도 제외** — 시뮬레이션 결과가 동일한데 매칭 필터로 쓰면 데이터를 무의미하게 희소화시킴
- 향후 simglucose가 Visentin 2020 모델(β-cell 파라미터 포함)로 업그레이드되거나, 종단 임상 데이터 기반의 progression 모델이 나오면 재평가할 수 있음

---

## 6. 부록: 향후 검토 사항

### 6.1 매칭 풀 통계 시뮬레이션
실제 사용자 데이터가 쌓이기 전, 시뮬레이터로 가상 코호트를 생성해서:
- 4/4 AND 매칭의 경우의 수 (= `3 × 3 × 4 × |weight bins|` 정도)
- 각 조합별 가상 환자 분포가 균등한지 / 희소 조합은 어디인지 사전 파악

### 6.2 가중치 점수화 방식 비교
현재는 단계적 완화(hard threshold)를 채택했지만, 대안으로:
- 4개 기준을 정규화 후 가중합 점수로 환산
- 임계 점수 이상이면 매칭

| 방식 | 장점 | 단점 |
|------|------|------|
| 단계적 완화 (현재) | 명확, 디버깅 쉬움, "왜 매칭됐는지" 설명 가능 | 경계 인근 환자 컷오프가 인위적 |
| 가중치 점수화 | 부드러운 매칭, 데이터 활용도 높음 | "75점이 70점보다 비슷한 이유"가 직관적이지 않음 |

데이터가 쌓이고 매칭 품질을 평가할 수 있게 되면 재검토.

### 6.3 β-cell 파라미터 추가 고려
- Visentin 2020에서 T2D 진단의 핵심 변수로 식별된 `Φs`, `Φd`
- 현재 simglucose는 Dalla Man 2007 모델 기반이라 이 파라미터가 없음
- 시뮬레이터 자체를 2020 모델로 업그레이드한다면 유사도 기준에 추가 검토 가치

---

## 참고 자료

- Dalla Man, C., Rizza, R. A., Cobelli, C. (2007). *Meal Simulation Model of the Glucose-Insulin System*. IEEE Transactions on Biomedical Engineering, 54(10), 1740–1749.
- Visentin, R., Cobelli, C., Dalla Man, C. (2020). *The Padova Type 2 Diabetes Simulator from Triple-Tracer Single-Meal Studies*. Diabetes Technology & Therapeutics, 22(12), 892–903.
- American Diabetes Association. *Standards of Care in Diabetes 2024*. Diabetes Care, 47(Supplement 1).
- The Look AHEAD Research Group (2013). *Cardiovascular Effects of Intensive Lifestyle Intervention in Type 2 Diabetes*. NEJM, 369(2), 145–154.
- 코드 참조:
  - [`simglucose/patient/persona_builder.py`](../simglucose/patient/persona_builder.py)
  - [`simglucose/params/{t1d,t2d,normal}.csv`](../simglucose/params/)
