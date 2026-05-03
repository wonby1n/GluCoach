# 환자 모델·파라미터·users.csv 종합 레퍼런스

본 프로젝트가 사용하는 세 가지 환자 시뮬레이션 모델(T1D / T2D / Normal)의 ODE 파라미터 전체 정의, 모델 간 차이, 데이터셋 저장 스키마, 그리고 사용자 입력에서 ODE 계수로 가는 변환 규칙을 한 군데에서 정리한 문서.

각 파라미터의 수학적 정의는 다음 논문에 근거한다:
- T1D / Normal: Dalla Man, Rizza, Cobelli (2007), *IEEE TBME* 54(10):1740-1749 — ["Meal Simulation Model of the Glucose-Insulin System"](https://ieeexplore.ieee.org/abstract/document/4303268/)
- T1D 갱신·시뮬레이터 디테일: Kovatchev et al. (2014), *J Diabetes Sci Technol* — ["The UVA/PADOVA Type 1 Diabetes Simulator: New Features"](https://journals.sagepub.com/doi/10.1177/1932296813514502)
- T2D: Visentin et al. (2020), *Diabetes Tech & Therapeutics* 22(12):892-903 — ["The Padova Type 2 Diabetes Simulator from Triple-Tracer Single-Meal Studies"](https://doi.org/10.1089/dia.2020.0110)

---

## 핵심 요약

- **`users.csv` = 14 페르소나 입력 + 3 메타 (총 17 컬럼)**
- **ODE 파라미터는 모델마다 다름**: T1D 62 / T2D 54 / Normal 38 컬럼
- **페르소나 → ODE 변환**: 14 입력 중 5~7개만 ODE 7~9개에 영향. 나머지 30+ ODE 파라미터는 cohort fit 값 그대로 사용 (페르소나 무관).

---

## 1. 세 환자 모델 개요

| 모델 | 베이스 논문 | 컬럼 | 행수 | 핵심 특징 |
|---|---|---|---|---|
| **T1D** | Dalla Man 2007 (UVA/Padova FDA-approved) | 62 | 30 (10×3 연령군) | 인슐린 펌프 기반. 13개 초기상태. 베타-세포 분비 없음 |
| **T2D** | Visentin 2020 | 54 | 10 | 베타-세포 분비 모델, 3-구획 인슐린 동역학, 포도당 의존 간 추출. 15개 초기상태 |
| **Normal** | Dalla Man 2007 단순화 | 38 | 11 | 정상상태 자동 유도(초기상태 컬럼 없음). 건강인 베타-세포 분비. 인슐린 외부 주입 없음 |

> **컬럼 수 차이의 본질**: 다른 ODE 시스템 = 다른 상태변수·파라미터. 같은 사람을 세 다른 수식으로 모델링한 결과.

---

## 2. `users.csv` 스키마

총 **17 컬럼**. CSV 파일에서의 실제 순서:
```
source, user_id, [페르소나 14개], category
```

### 메타 (3개)

| 컬럼 | CSV 위치 | 예 | 설명 |
|---|---|---|---|
| `source` | 1번째 | `simulator` | 데이터 출처 라벨 (실 데이터 통합 시 구분용) |
| `user_id` | 2번째 | `sim_0042` | 4자리 zero-padded 식별자 |
| `category` | **17번째 (마지막)** | `T2D_hi_diabetes_heavy` | 카테고리 라벨. `<type>_<activity>_<fbg_bin>_<weight_bin>` 형식 |

### 페르소나 14개

| # | 컬럼 | 타입/예 | ODE 영향 | 비고 |
|---|---|---|---|---|
| 1 | `diabetes_type` | `T1D`/`T2D`/`Normal` | 간접: 어느 cohort csv 읽을지 | |
| 2 | `birthdate` | `1990-01-01` | 간접: T1D만 child/adolescent/adult 분기 | |
| 3 | `sex` | `M`/`F` | 없음 | informational + 키 분포 조건부 샘플링용 |
| 4 | `height_cm` | float | 없음 | informational. BMI 계산용 |
| 5 | `weight_kg` | float | **직접**: ODE의 `BW` | 덮어쓰기 |
| 6 | `treatment` | `insulin`/`medication`/`diet` | 없음 (단, 시뮬 경로 선택) | insulin → 펌프+컨트롤러, diet → meal-only |
| 7 | `medication_timing` | `식전`/`식후`/`식사 직후`/null | **직접 (T2D만)**: `kabs` 곱셈 | T2D + medication일 때만 |
| 8 | `medication` | "메트포르민" 등 / null | 없음 | informational |
| 9 | `diagnosis_years` | float | 없음 | informational |
| 10 | `hba1c` | float / null | 조건부: `Gb` (fasting_bg가 비었을 때만) | Gb = 28.7 × hba1c − 46.7 (ADAG) |
| 11 | `fasting_bg` | float | **직접**: `Gb`, `x0_4`, `x0_13`(T2D는 `x0_15`) | Gb 직접, 초기상태 비례 보정 |
| 12 | `activity` | `low`/`medium`/`high` | **직접**: `Vmx`, `p2u`(T2D `p2U`), `kabs` 곱셈 | 아래 곱셈 표 |
| 13 | `meal_pattern` | 11개 카테고리 (§3.4) | 없음 (단, **식사 이벤트 시간·끼니 수를 결정론적으로** 정의) | 1일 시뮬에서 끼니 구성 결정 |
| 14 | `target_bg` | float | 없음 | 인슐린 컨트롤러 setpoint |

---

## 3. 14 페르소나 입력 → ODE 파라미터 변환 규칙

[`simglucose/patient/persona_builder.py:build_params_from_persona`](../simglucose/patient/persona_builder.py)에 구현됨.

### 3.1 매핑 표

| 페르소나 입력 | 영향받는 ODE 파라미터 | 변환 규칙 |
|---|---|---|
| `diabetes_type` | (어느 cohort csv 읽을지) | t1d/t2d/normal 분기 |
| `birthdate` (age) | (T1D 템플릿 분기) | age<13 → child#001 / <18 → adolescent#001 / 그 외 → adult#001 |
| `weight_kg` | `BW` | 직접 덮어쓰기 |
| `fasting_bg` | `Gb`, `x0_4`, `x0_13`(T2D는 `x0_15`) | Gb 직접; 초기상태는 `x_new = x_template × (Gb_new / Gb_template)` 비례 보정 |
| `hba1c` | `Gb` (fasting_bg 없을 때만) | `Gb = 28.7 × hba1c − 46.7` (ADAG 식) |
| `activity` | `Vmx`, `p2u`/`p2U`, `kabs` | 곱셈 (아래 표) |
| `medication_timing` (T2D만) | `kabs` | 식전 ×1.1, 식후 ×1.0, 식사 직후 ×0.85 |
| `treatment` | (시뮬 경로 선택) | insulin → `T1DSimEnv` + 컨트롤러, diet → meal-only loop |
| 그 외 6개 (sex, height_cm, medication, diagnosis_years, meal_pattern, target_bg) | 없음 | informational 또는 시나리오/컨트롤러용 |

### 3.2 Activity 곱셈

| activity | `Vmx` ×= | `p2u` (또는 `p2U`) ×= | `kabs` ×= |
|---|---|---|---|
| `low` | 0.7 | 0.8 | 0.9 |
| `medium` | 1.0 | 1.0 | 1.0 |
| `high` | 1.5 | 1.2 | 1.1 |

### 3.3 Normal 전용 후처리

페르소나 적용 후 `m6`, `Vm0`을 정상상태 식으로 재유도. BW/Gb 변경에 따른 일관성 유지를 위함.

### 3.4 meal_pattern 11개 카테고리

[`simglucose/dataset/scenario_sampler.py`](../simglucose/dataset/scenario_sampler.py)에서 정의. 한 명의 페르소나는 **1일치** 식사 이벤트를 받으며, meal_pattern이 그날의 끼니 구성을 결정론적으로 결정한다 (시간 jitter만 변동).

| 라벨 | 1일 끼니 | 시간 jitter | 비고 |
|---|---|---|---|
| `regular_3` | B+L+D | ±30분 | 정상 식습관 |
| `irregular` | B+L+D | ±2시간 | 시간이 들쭉날쭉 |
| `frequent_small` | B+L+D + 간식 2회 (10시·15시), 메인 mean ×0.7 | ±30분 (간식 ±30분) | 자주 작게 먹음 |
| `skip_breakfast` | L+D | ±30분 | 아침 거름 |
| `skip_lunch` | B+D | ±30분 | 점심 거름 |
| `skip_dinner` | B+L | ±30분 | 저녁 거름 |
| `skip_breakfast_lunch` | D만 | ±30분 | OMAD 저녁형 |
| `skip_breakfast_dinner` | L만 | ±30분 | 낮 한 끼 |
| `skip_lunch_dinner` | B만 | ±30분 | 아침만 |
| `fasting_day` | 0끼 | — | 종일 단식 |
| `late_dinner` | B+L+D, 저녁 22:00 | ±30분 | 야식형 |

기본 끼니 시간: 아침 08:00 / 점심 12:00 / 저녁 19:00 (late_dinner는 22:00).

탄수 g 분포 (truncated normal):
- 아침: mean=50 std=15 [25, 100]
- 점심: mean=60 std=15 [25, 100]
- 저녁: mean=65 std=18 [25, 100]
- 간식: mean=20 std=5 [10, 40]

---

## 4. 전체 파라미터 정의

각 파라미터의 의미·단위·적용 모델·해당 ODE 식에서의 역할.

표기:
- **단위 표기**: `min⁻¹` = per minute, `mg/dL` = milligrams per deciliter, `pmol/L` = picomoles per liter
- **적용 모델**: T1D / T2D / Normal 중 어느 csv에 등장하는지
- 일부 파라미터는 csv에 있지만 코드에서 사용되지 않거나 출처 불명 — 그대로 명시

### 4.1 메타·식별

| 파라미터 | 모델 | 의미 |
|---|---|---|
| `Name` | 모두 | 환자 식별자 문자열 (예: `adult#001`, `t2d#003`, `normal_adult#005`) |
| `i` | T1D, Normal | 행 인덱스 (정수) |
| `patient_history` | T1D | 메타 노트. 코드에서 사용 안 됨 |

### 4.2 신체 조성

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `BW` | kg | 모두 | **체중**. 페르소나의 `weight_kg`로 직접 덮어씀. ODE의 거의 모든 농도식이 BW로 정규화됨 |

### 4.3 포도당 농도·분포 (혈장·조직)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `Gb` | mg/dL | 모두 | **기저 혈장 포도당 농도** (정상상태 값). 페르소나의 `fasting_bg`로 덮어씀. `Gb = Gpb / Vg` |
| `Gpb` | mg/kg | T1D | 혈장 + 빠르게 평형화되는 조직의 기저 포도당 질량. `Gpb = Gp(0)` |
| `Gtb` | mg/kg | T1D | 천천히 평형화되는 조직의 기저 포도당 질량. `Gtb = Gt(0) = (Fcns − EGPb + k1·Gpb)/k2` |
| `Vg` (T1D/Normal), `VG` (T2D) | dL/kg | 모두 | **포도당 분포 부피**. `Gb = Gp/Vg` 관계 |

### 4.4 인슐린 농도·분포 (혈장·간·말초)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `Ib` | pmol/L | 모두 | **기저 혈장 인슐린 농도**. `Ib = Ip / Vi` |
| `Ipb` | pmol/kg | T1D | 혈장 인슐린 질량의 기저값. `Ipb = Ip(0)` |
| `Ilb` | pmol/kg | T1D | 간 인슐린 질량의 기저값. `Ilb = Il(0)` |
| `Vi` (T1D/Normal), `VI` (T2D) | L/kg | 모두 | **인슐린 분포 부피** |
| `VC` | L/kg | T2D만 | **중앙 인슐린 구획 부피** (Visentin 3-구획 인슐린 동역학) |

### 4.5 포도당 동역학 (조직 ↔ 혈장)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `k1` | min⁻¹ | 모두 | 혈장 → 조직 transport rate. `dGt/dt`에 등장 |
| `k2` | min⁻¹ | 모두 | 조직 → 혈장 transport rate. `dGp/dt`에 등장 |

### 4.6 인슐린 동역학 (청소·작용)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `m1` | min⁻¹ | 모두 | 간 인슐린 청소 rate (`Il` 변수에 작용) |
| `m2` | min⁻¹ | 모두 | 인슐린 청소 rate (`Il` 변수). `m2 = (Sb/Ipb − m4/(1−HEb)) × (1−HEb)/HEb`. 분비 S=0 가정 시 `m2 = −m4/HEb` |
| `m4` | min⁻¹ | 모두 | 말초 인슐린 분해. 일반적으로 `m4 = (2/5) × (Sb/Ipb) × (1−HEb)` |
| `m5` | min·kg/pmol | 모두 | 인슐린 동역학 rate parameter (HE 계산에 사용) |
| `m6` | dimensionless | T2D, Normal | HE 동역학에서 m3와 결합. Normal은 페르소나 적용 후 재유도 |
| `m30` | min⁻¹ | T1D | **m3의 초기값**. 실제 m3는 시간에 따라 변동 (`m3(0) = HEb·m1/(1−HEb)`, `m3(t) = HE(t)·m1/(1−HE(t))`) |
| `ki` | min⁻¹ | 모두 | **인슐린 작용 지연 rate**. 인슐린이 작용 부위에 도달하는 지연 동역학 |
| `HEb` | dimensionless | T1D, Normal | **간 인슐린 추출 baseline**. `HEb = HE(0) = −m5·S(0) + m6` |
| `CL` | — | T1D | 코드에서 사용 안 됨 (vendor 문서 미상) |

### 4.7 내인성 포도당 생산 (EGP)

EGP 식: `EGP(t) = kp1 − kp2·Gp(t) − kp3·Id(t) − kp4·Ipo(t)` (Normal/T2D는 4개 항, T1D는 kp4 없음)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `EGPb` | mg/kg/min | T1D | **기저 EGP** (정상상태). `EGPb = Ub + Eb`. 정상인은 0 |
| `kp1` | mg/kg/min | 모두 | 포도당·인슐린이 0일 때의 외삽 EGP (절편) |
| `kp2` | min⁻¹ | 모두 | **간 포도당 효과**. `dEGP/dGp` 음의 효과 |
| `kp3` | mg/kg/min per pmol/L | 모두 | **간 인슐린 작용 진폭**. 인슐린 농도가 EGP를 억제 |
| `kp4` | mg/kg/min per pmol/kg | T2D, Normal | **문맥 인슐린의 EGP 효과**. T1D에는 없음 |

### 4.8 인슐린 감수성·이용 (Glucose Utilization)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `Vmx` | mg/kg/min per pmol/L | 모두 | **말초 인슐린 작용 진폭** (insulin sensitivity). 활동수준에 따라 곱셈 보정 |
| `Km0` | mg/kg | 모두 | 포도당 이용 식의 Michaelis-Menten 상수 |
| `Vm0` | mg/kg/min | T1D, Normal | **기저 인슐린에서의 최대 이용**. `Vm0 = (EGPb − Fcns) × (Km0 + Gtb)/Gtb` |
| `p2u` (T1D/Normal), `p2U` (T2D) | min⁻¹ | 모두 | **인슐린 작용 지연** (작용 부위 도달 지연). 활동도 곱셈 |

### 4.9 신장 청소

신장 배설: `E(t) = ke1·(Gp − ke2)` if `Gp > ke2`, 그 외 0

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `ke1` | min⁻¹ | 모두 | 신장 청소 rate constant |
| `ke2` | mg/kg | 모두 | 신장 배설 역치 (이 값 이하에서 배설 0) |

### 4.10 위장 흡수 (Rate of Appearance)

식사 후 위→장→혈장으로 가는 포도당 흡수.

`kempt(Qsto) = kmin + (kmax−kmin)/2 × {tanh(α(Qsto − b·D)) − tanh(β(Qsto − d·D)) + 2}`

`Ra(t) = f·kabs·Qgut/BW`

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `kabs` | min⁻¹ | 모두 | **장 흡수 rate constant**. 활동도·약 복용 시점으로 곱셈 보정 |
| `kmax` | min⁻¹ | 모두 | 위 비움 rate의 최대값 |
| `kmin` | min⁻¹ | 모두 | 위 비움 rate의 최소값 |
| `b` | dimensionless | 모두 | 위 비움 함수의 파라미터 (전이 중점 1) |
| `d` | dimensionless | 모두 | 위 비움 함수의 파라미터 (전이 중점 2) |
| `f` | dimensionless | 모두 | **흡수 분율** (장에서 혈장으로 들어가는 비율) |
| `dosekempt` | dimensionless | T1D | 위 비움 normalize 상수 (Q_sto = stomach glucose 양 정규화) |

### 4.11 중추신경계 포도당 소비

뇌·적혈구의 인슐린 비의존 포도당 이용. 일정 상수.

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `Fsnc` (T1D/Normal), `Fcns` (T2D) | mg/kg/min | 모두 | **CNS 포도당 소비** (정확한 이름은 `F_cns`. T1D csv는 typo). `Uii(t) = Fcns` |

### 4.12 피하 인슐린 동역학 (T1D 펌프 전용)

피하 주사된 인슐린이 혈장으로 도달하는 2단계 동역학:

`dIsc1/dt = −(kd + ka1)·Isc1 + IIR(t)`

`dIsc2/dt = kd·Isc1 − ka2·Isc2`

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `kd` | min⁻¹ | T1D | Isc1 → Isc2 전이 rate |
| `ka1` | min⁻¹ | T1D | Isc1 → 혈장 흡수 rate |
| `ka2` | min⁻¹ | T1D | Isc2 → 혈장 흡수 rate |
| `ksc` | min⁻¹ | T1D, Normal | **피하 포도당** 동역학 rate (CGM 측정용 — 혈장 → 피하조직 지연) |
| `isc1ss` | pmol/kg | T1D | Isc1의 정상상태 초기값 |
| `isc2ss` | pmol/kg | T1D | Isc2의 정상상태 초기값 |
| `u2ss` | pmol/min/kg | T1D | 기저 인슐린 정상상태 (`basal = u2ss × BW / 6000`로 펌프 baseline 계산) |

### 4.13 베타-세포 분비 (Normal — 건강인 모델)

건강인의 인슐린 분비 동역학 (1차·2차 분비). `S(t) = γ·Ipo(t)` 식의 보조 파라미터들.

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `K` | dimensionless | Normal | 1차 분비의 진폭 (포도당 농도 증가율에 비례) |
| `alpha` | min⁻¹ | Normal | 2차 분비의 시간상수 (지연 동역학) |
| `beta` | dimensionless | Normal | 2차 분비의 정적 진폭 |
| `gamma` | min⁻¹ | Normal | Ipo → S 변환 rate |

> **주의**: T2D csv의 `alpha`는 다른 의미 (포도당 의존 간 추출). 같은 이름이지만 모델별 해석 다름. § 6.3 참조.

### 4.14 베타-세포 분비 (T2D — Visentin 모델)

T2D 환자의 베타-세포 기능 부전을 표현하는 분비 모델.

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `Fs` | dimensionless | T2D | **1차 분비 진폭** (static phase) |
| `Fd` | min | T2D | **2차 분비 시간상수** (dynamic phase) |
| `h` | mg/dL | T2D | **분비 역치** (이 농도 위에서 베타-세포 활성) |
| `aG` | dimensionless | T2D | 정상상태 보정 인자 (포도당 의존성) |

### 4.15 T2D 3-구획 인슐린 동역학

Visentin 모델의 인슐린 분포는 단순 plasma 외 추가 구획을 가짐.

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `k01` | min⁻¹ | T2D | 중앙 구획 → 외부 청소 rate |
| `k12` | min⁻¹ | T2D | 중앙 → 말초 구획 transfer rate |
| `k21` | min⁻¹ | T2D | 말초 → 중앙 구획 transfer rate |

### 4.16 T2D `alpha` (간 추출)

| 파라미터 | 단위 | 모델 | 의미 |
|---|---|---|---|
| `alpha` (T2D) | dimensionless | T2D | **포도당 의존 간 추출** 파라미터. 혈당이 증가할수록 간의 인슐린 추출률이 변화. **Normal의 `alpha`와 의미 완전히 다름** |

### 4.17 초기 상태값 (x0_*)

ODE 시뮬레이션의 시작 시점 상태 벡터. csv에 저장되어 시뮬 시작 시 그대로 로드.

| 파라미터 | 모델 | 일반적 의미 (T1D Dalla Man 2007 기준) |
|---|---|---|
| `x0_1` (또는 `x0_ 1`) | T1D, T2D | Qsto1 — 위의 고체 포도당 (mg) |
| `x0_2` | T1D, T2D | Qsto2 — 위의 액체 포도당 (mg) |
| `x0_3` | T1D, T2D | Qgut — 장의 포도당 (mg) |
| `x0_4` | T1D, T2D | **Gp — 혈장 포도당 (mg/kg)**. Gb 변경 시 비례 보정 |
| `x0_5` | T1D, T2D | Gt — 조직 포도당 (mg/kg) |
| `x0_6` | T1D, T2D | Ip — 혈장 인슐린 (pmol/kg) |
| `x0_7` | T1D, T2D | X — 작용 부위 인슐린 (pmol/L) |
| `x0_8` | T1D, T2D | Id1 — 인슐린 지연 1 (pmol/L) |
| `x0_9` | T1D, T2D | Id2 — 인슐린 지연 2 (pmol/L) |
| `x0_10` | T1D, T2D | Il — 간 인슐린 (pmol/kg) |
| `x0_11` | T1D, T2D | Ipo — 문맥 인슐린 (pmol/kg) |
| `x0_12` | T1D, T2D | Y — 지연 포도당 신호 (pmol/kg/min) |
| `x0_13` (T1D), `x0_15` (T2D) | T1D, T2D | **Gsc — 피하 포도당 (mg/kg)**. Gb 변경 시 비례 보정 |
| `x0_13`, `x0_14` (T2D) | T2D | T2D 추가 상태 (Visentin 모델의 인슐린 추가 구획) |

> **Normal에는 x0_* 컬럼이 없다**. Normal Patient 클래스가 정상상태 식을 런타임에 풀어 자동 유도하기 때문 ([`simglucose/patient/normal.py`](../simglucose/patient/normal.py)).

### 4.18 코드에서 사용되지 않는 파라미터

csv에는 있으나 시뮬레이션 코드에서 참조하지 않는 컬럼들:

| 파라미터 | 모델 | 비고 |
|---|---|---|
| `Rdb` | T1D | vendor 문서 미상 |
| `PCRb` | T1D | vendor 문서 미상 |
| `CL` | T1D | vendor 문서 미상 |
| `patient_history` | T1D | 메타 노트 |

---

## 5. 모델 간 비교 매트릭스

✓ = 컬럼 존재 / − = 없음. T2D 대문자 변형(`VG`, `VI`, `p2U`)은 소문자 정식 이름으로 정규화.

### 5.1 세 모델 모두에 있는 공통 파라미터 (27개)

| 파라미터 | T1D | T2D | Normal |
|---|---|---|---|
| `BW` | ✓ | ✓ | ✓ |
| `Gb` | ✓ | ✓ | ✓ |
| `Ib` | ✓ | ✓ | ✓ |
| `Vg`/`VG` | ✓ | ✓ | ✓ |
| `Vi`/`VI` | ✓ | ✓ | ✓ |
| `Vmx` | ✓ | ✓ | ✓ |
| `Km0` | ✓ | ✓ | ✓ |
| `p2u`/`p2U` | ✓ | ✓ | ✓ |
| `k1`, `k2` | ✓ | ✓ | ✓ |
| `kabs` | ✓ | ✓ | ✓ |
| `kmax`, `kmin` | ✓ | ✓ | ✓ |
| `b`, `d`, `f` | ✓ | ✓ | ✓ |
| `m1`, `m2`, `m4`, `m5` | ✓ | ✓ | ✓ |
| `kp1`, `kp2`, `kp3` | ✓ | ✓ | ✓ |
| `ki` | ✓ | ✓ | ✓ |
| `ke1`, `ke2` | ✓ | ✓ | ✓ |
| `Name` | ✓ | ✓ | ✓ |

### 5.2 두 모델만 공유

| 파라미터 | T1D | T2D | Normal | 비고 |
|---|---|---|---|---|
| `Fsnc`/`Fcns` | ✓ | ✓ (다른 이름) | ✓ | T1D는 `Fsnc` (오타), T2D는 `Fcns` (정확), Normal은 `Fsnc` |
| `HEb` | ✓ | − | ✓ | |
| `Vm0` | ✓ | − | ✓ | |
| `i` | ✓ | − | ✓ | |
| `ksc` | ✓ | − | ✓ | |
| `kp4` | − | ✓ | ✓ | |
| `m6` | − | ✓ | ✓ | |
| `alpha` | − | ✓ | ✓ | **이름 동일하나 의미 다름** (§ 6.3) |
| `x0_1`~`x0_13` | ✓ | ✓ | − | Normal은 정상상태 자동 유도 |

### 5.3 모델별 고유

#### T1D 전용 (17)
`EGPb`, `Gpb`, `Gtb`, `Ipb`, `Ilb`, `m30`, `CL`, `Rdb`, `PCRb`, `kd`, `ka1`, `ka2`, `dosekempt`, `u2ss`, `isc1ss`, `isc2ss`, `patient_history`

#### T2D 전용 (11)
`Fs`, `Fd`, `h`, `aG`, `VC`, `k01`, `k12`, `k21`, `x0_14`, `x0_15`, `(VG/VI/p2U` 대문자만 별개로 보면)

#### Normal 전용 (3)
`K`, `beta`, `gamma`

---

## 6. 명명 변형 주의

### 6.1 T2D의 대문자 변형

T1D / Normal은 소문자, T2D만 같은 양을 대문자로 표기:

| T1D / Normal | T2D | 의미 |
|---|---|---|
| `Vg` | `VG` | 포도당 분포 부피 |
| `Vi` | `VI` | 인슐린 분포 부피 |
| `p2u` | `p2U` | 인슐린 작용 지연 |

[`persona_builder.py`](../simglucose/patient/persona_builder.py)의 `_T2D_KEY_MAP = {"Vg": "VG", "Vi": "VI", "p2u": "p2U"}`이 이 변환을 처리.

### 6.2 `Fcns` vs `Fsnc`

- 정확한 이름: `F_cns` (central nervous system glucose utilization)
- T1D / Normal csv: `Fsnc` (T1D 원본의 typo가 그대로 전파)
- T2D csv: `Fcns` (정확한 표기)

### 6.3 `alpha` — 동명이의어 ⚠️

**같은 이름이지만 모델별로 의미가 완전히 다름**:
- **T2D의 `alpha`**: 포도당 의존 간 인슐린 추출 (hepatic extraction) 파라미터
- **Normal의 `alpha`**: 2차 베타-세포 분비의 시간상수

코드와 논문에서 모델 컨텍스트로 구분. 같은 분포에서 sample하지 않도록 주의.

---

## 7. 페르소나 생성·시뮬 흐름 한눈에

```
[사용자 자연어 입력 또는 폼 입력]
         │
         ▼
[14개 페르소나 dict] ←── meal_pattern은 11개 중 무작위 (또는 자연어로 지정)
         │
         │ persona_builder.build_params_from_persona
         │   ├─ diabetes_type → cohort csv 선택 (params/{t1d,t2d,normal}.csv)
         │   ├─ age → T1D 템플릿 분기 (#001 행 로드)
         │   ├─ weight_kg → BW 덮어쓰기
         │   ├─ fasting_bg → Gb 덮어쓰기 + x0_4, x0_13 비례 보정
         │   ├─ activity → Vmx, p2u, kabs 곱셈
         │   ├─ medication_timing → kabs 곱셈 (T2D)
         │   └─ Normal: m6, Vm0 재유도
         ▼
[ODE 38(or 54, 62)개 파라미터 — 메모리에만 존재]
         │
         │ scenario_sampler.sample_scenario(rng, meal_pattern=...)
         │   └─ meal_pattern → 1일치 식사 이벤트 (끼니 구성·시간) 결정
         ▼
[T1DPatient/T2DPatient/NormalPatient(params), 1일치 식사 시나리오]
         │
         ▼
[ODE 적분 (24h, 1분 step) → BG, CGM, CHO 시계열]
         │
         ▼
[unified_schema 변환 → glucose_readings·meal_events·users 행]
         │
         ▼
[data/simulator/*.csv 저장]
```

페르소나당 시뮬 기간은 **1일 고정**. 끼니 구성은 meal_pattern이 결정하고, 시간만 jitter로 변동한다.

---

## 8. 자주 헷갈리는 점

> **Q. 14개 입력이 38개 파라미터의 부분집합인가?**
> **A.** 아니다. 다른 추상화 레벨. 14개는 임상 페르소나(체중·공복혈당·활동), 38개는 ODE 계수(BW, Gb, kp3, ...). 14개 중 5~7개만 38개의 일부에 영향.

> **Q. 같은 카테고리에서 N명을 뽑으면 ODE 파라미터가 다 다른가?**
> **A.** 아니다. 같은 cohort fit (예: `adult#001`) 행에서 시작하므로 30+ 파라미터는 N명 모두 동일. 페르소나가 흔드는 7~9개만 차이남. 같은 카테고리의 cohort 다양성은 본질적으로 BW, Gb, activity 곱셈 5개에서만 옴.

> **Q. T1D 컬럼 62개에는 BMI가 있나?**
> **A.** 없다. ODE는 BMI를 모름. `BW`(체중)만 받음. BMI는 임상 카테고리 분류 시점에서나 의미.

> **Q. 페르소나 override 적용된 ODE 값이 어디 저장되나?**
> **A.** 어디에도 저장 안 됨. 메모리에서만 살았다가 시뮬 끝나면 GC. `users.csv`의 14개 + 시뮬레이터 코드만 있으면 언제든 결정적으로 재구성 가능.

> **Q. 세 모델이 받는 페르소나 입력 14개가 동일한 이유는?**
> **A.** 14개는 사람에게 물어볼 수 있는 임상/생활 입력 (체중, 공복혈당, 활동수준 등)이라 모델과 무관하게 누구에게나 적용. 38개 ODE 계수는 모델 엔진의 내부 좌표라 모델마다 다름. `persona_builder`가 두 레이어 사이의 번역기 역할.

> **Q. 새 환자 모델을 추가하려면 어떻게 하나?**
> **A.** (1) `simglucose/patient/<new>.py`에 ODE 클래스 작성, `params/<new>.csv` 추가. (2) `simglucose/patient/factory.py`의 `_REGISTRY`에 등록. (3) `persona_builder.py`에 분기 추가 (필요 시). (4) `params/README.md`에 출처 명시. 14개 페르소나 인터페이스는 그대로.

---

## 9. 관련 파일

| 파일 | 역할 |
|---|---|
| [`params/t1d.csv`](../params/t1d.csv) | T1D cohort fit 값 (30명) |
| [`params/t2d.csv`](../params/t2d.csv) | T2D cohort fit 값 (10명) |
| [`params/normal.csv`](../params/normal.csv) | Normal cohort fit 값 (11명) |
| [`params/README.md`](../params/README.md) | params 폴더 소유권·기원 |
| [`simglucose/_paths.py`](../simglucose/_paths.py) | params/ 경로 resolver |
| [`simglucose/patient/factory.py`](../simglucose/patient/factory.py) | 모델 팩토리 + `get_params_path` |
| [`simglucose/patient/persona_builder.py`](../simglucose/patient/persona_builder.py) | 14 페르소나 → 38 ODE 변환 |
| [`simglucose/patient/t1d.py`](../simglucose/patient/t1d.py) | T1D ODE 시스템 |
| [`simglucose/patient/t2d.py`](../simglucose/patient/t2d.py) | T2D ODE 시스템 |
| [`simglucose/patient/normal.py`](../simglucose/patient/normal.py) | Normal ODE 시스템 |
| [`simglucose/dataset/unified_schema.py`](../simglucose/dataset/unified_schema.py) | `users.csv` 행 변환 |
| [`simglucose/dataset/orchestrator.py`](../simglucose/dataset/orchestrator.py) | `category` 컬럼 추가 + CSV 저장 |
