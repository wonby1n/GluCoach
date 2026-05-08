# 환자 cohort 파라미터 (프로젝트 자산)

이 디렉터리는 **본 프로젝트가 관리하는 ODE 환자 cohort** 파일을 담는다.
`simglucose/params/`(라이브러리 vendor 자산)와 분리되어 있다.

## 파일별 출처

| 파일 | 출처 | 행 수 | 상태 |
|---|---|---|---|
| `t1d.csv` | UVA/Padova 시뮬레이터 (Dalla Man et al. 2014, FDA-approved) child#001~010, adolescent#001~010, adult#001~010 | 30 | vendor에서 가져온 스냅샷, 본 프로젝트가 사용 |
| `t2d.csv` | Visentin et al. 2020 기반으로 본 프로젝트가 추가 | 10 | 프로젝트 자체 |
| `normal.csv` | 본 프로젝트가 정의한 정상인 cohort | 11 | 프로젝트 자체 |

## 왜 simglucose/params/ 와 분리했나

- `simglucose/params/`는 **라이브러리 인프라 자료** (CGM 센서 모델, 인슐린 펌프 스펙, 식사 quest data)
- 환자 cohort 파라미터는 **이 프로젝트의 핵심 자산** — 어떤 환자 모집단을 시뮬할지 결정
- 폴더 분리로 "라이브러리 vs 프로젝트" 경계가 명확

## 어떤 코드가 읽나

`simglucose.patient.factory.get_params_path(patient_type)`가 단일 진입점.
모든 다른 모듈(`t1d.py`, `t2d.py`, `normal.py`, `persona_builder.py` 등)은 이 함수를 거쳐서 경로를 얻는다.

```python
from simglucose.patient.factory import get_params_path
path = get_params_path("T1D")  # → <project_root>/params/t1d.csv
```

## 컬럼 의미

각 행은 한 명의 가상 환자에 대한 ODE 파라미터 셋. 컬럼 정의는
[`docs/definitions_of_vpatient_parameters.md`](../docs/definitions_of_vpatient_parameters.md) 참조.

페르소나 입력 14개에서 ODE 38개로 가는 변환 규칙은
[`simglucose/patient/persona_builder.py`](../simglucose/patient/persona_builder.py)에 정의됨.
