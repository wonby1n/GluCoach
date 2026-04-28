# AI Glucose Predict API 인터페이스 정의

> **작성일**: 2026-04-24

---

## 1. 개요

식전 예측 API(`POST /api/predict/glucose`)가 호출할 **AI Glucose Predict API**의 요청·응답 스키마를 정의합니다.
BE는 이 인터페이스를 기반으로 어댑터를 구현하며, AI 파트는 이 스키마에 맞춰 추론 엔드포인트를 제공합니다.

### 식전 예측 vs 식후 기록 — 역할 구분

| | 식전 예측 API (본 문서) | 식후 혈당 반응 기록 API (MEAL-BE) |
|---|---|---|
| **시점** | 식사 **전** | 식사 **후** (2시간 경과) |
| **목적** | "이걸 먹으면 혈당이 어떻게 될까?" 시뮬레이션 | 실제 섭취 후 CGM 실측 혈당 반응 저장 |
| **데이터 흐름** | BE → AI 모델 호출 → **예측 곡선 반환** | CGM 실측 데이터 → **DB 저장** |
| **DB 테이블** | `glucose_predictions` (curve_json, peak_pred) | `meal_glucose_responses` (peak_mgdl, return_min, grade) |
| **AI 의존** | O (모델 추론 필요) | X (실측값 기록만) |
| **BE 엔드포인트** | `POST /api/predict/glucose` | 별도 스토리에서 구현 |

> 두 API는 나중에 연결됩니다: 식후 실측 데이터로 식전 예측의 `accuracy_pct`를 역산하여 모델 정확도를 평가합니다.

### 흐름

```
  FE (정밀 시뮬레이터)          BE 서버                      AI 서버
  ─────────────────          ──────                      ──────
         │                      │                          │
         │  POST /api/predict/  │                          │
         │  glucose             │                          │
         │  (음식 + 유저 정보)  │                          │
         │ ──────────────────►  │                          │
         │                      │  POST /inference/glucose │
         │                      │  (영양 데이터 + 프로필)  │
         │                      │ ────────────────────────►│
         │                      │                          │
         │                      │   예측 곡선 + 메타       │
         │                      │ ◄────────────────────────│
         │                      │                          │
         │  정규화된 응답       │                          │
         │  (곡선 + 피크 +      │                          │
         │   복귀시간 + 신뢰도) │                          │
         │ ◄──────────────────  │                          │
         │                      │                          │
```

---

## 2. AI 엔드포인트

```
POST /inference/glucose
Content-Type: application/json
```

---

## 3. 요청 스키마

```json
{
  "food": {
    "foodId": "F001",
    "name": "흰쌀밥",
    "carbsG": 56.0,
    "sugarG": 0.3,
    "proteinG": 4.4,
    "fatG": 0.5,
    "kcal": 250.0,
    "giScore": 86
  },
  "userProfile": {
    "diabetesType": "TYPE2",
    "isMedicated": true,
    "height": 175.0,
    "weight": 72.0,
    "currentGlucose": 105.0,
    "riseRate": 2.5,
    "fallRate": -1.8,
    "spikeThreshold": 160.0
  }
}
```

### 필드 상세

#### `food` (음식 영양 데이터)

| 필드 | 타입 | 필수 | 설명 | 출처 |
|------|------|------|------|------|
| `foodId` | String | O | API 식품 고유 ID | foods 테이블 |
| `name` | String | O | 음식명 | foods 테이블 |
| `carbsG` | Double | O | 탄수화물 (g) | 식약처 I2790 (항상 제공) |
| `proteinG` | Double | O | 단백질 (g) | 식약처 I2790 (항상 제공) |
| `fatG` | Double | O | 지방 (g) | 식약처 I2790 (항상 제공) |
| `kcal` | Double | O | 칼로리 (kcal) | 식약처 I2790 (항상 제공) |
| `sugarG` | Double | △ | 당류 (g) | 식약처 I2790 (일부 식품 null) |
| `giScore` | Integer | △ | 혈당지수 GI (0~100) | 별도 매핑 테이블 (식약처 미제공) |

> **데이터 출처 정리**
> - `carbsG`, `proteinG`, `fatG`, `kcal`: 식품안전처 I2790 API에서 항상 제공 → **필수**
> - `sugarG`: I2790 API에서 일부 식품 null → **선택**
> - `giScore`: 식약처 API 미제공, 대한당뇨병학회 자료 등 별도 매핑 테이블로 확보 → **선택 (없으면 null)**

#### `userProfile` (유저 프로필)

| 필드 | 타입 | generic | personalized | 설명 | 출처 |
|------|------|---------|-------------|------|------|
| `diabetesType` | String | **필수** | **필수** | `NONE` / `TYPE1` / `TYPE2` | users 테이블 |
| `isMedicated` | Boolean | **필수** | **필수** | 당뇨약/인슐린 복용 여부 | users 테이블 |
| `height` | Float | 선택 | 선택 | 키 (cm), 신규 유저는 null 가능 | users 테이블 |
| `weight` | Float | 선택 | 선택 | 체중 (kg), 신규 유저는 null 가능 | users 테이블 |
| `currentGlucose` | Double | 선택 | **필수** | 현재 혈당 (mg/dL), CGM 최신값 | cgm_readings |
| `riseRate` | Double | 불필요 | **필수** | 평균 혈당 상승 속도 (mg/dL/min) | cgm_patterns |
| `fallRate` | Double | 불필요 | **필수** | 평균 혈당 하강 속도 (mg/dL/min) | cgm_patterns |
| `spikeThreshold` | Double | 불필요 | **필수** | 개인화 스파이크 임계값 | cgm_patterns |

> **모델별 필수 요건 정리**
> - **generic**: `diabetesType` + `isMedicated` + 음식 영양소만으로 예측 가능. `currentGlucose`가 없으면 평균 공복혈당(100mg/dL)을 기본값으로 사용.
> - **personalized**: 위 필수 필드 + `currentGlucose`(곡선 시작점) + CGM 패턴 3종(`riseRate`, `fallRate`, `spikeThreshold`)이 모두 필요. CGM 패턴은 `cgm_patterns.personalized_at`이 not null일 때만 존재.

---

## 4. 응답 스키마

```json
{
  "curve": [
    { "minuteOffset": 0,   "glucoseMgdl": 105.0 },
    { "minuteOffset": 5,   "glucoseMgdl": 112.0 },
    { "minuteOffset": 10,  "glucoseMgdl": 122.0 },
    { "minuteOffset": 15,  "glucoseMgdl": 135.0 },
    ...
    { "minuteOffset": 120, "glucoseMgdl": 110.0 }
  ],
  "peakMgdl": 168.0,
  "peakMinute": 45,
  "returnMinute": 95,
  "modelType": "personalized",
  "confidence": 0.82
}
```

### 필드 상세

| 필드 | 타입 | 설명 |
|------|------|------|
| `curve` | Array | 식후 2시간 예상 혈당 시계열 |
| `curve[].minuteOffset` | Integer | 식사 시점 기준 경과 시간 (분) |
| `curve[].glucoseMgdl` | Double | 해당 시점 예상 혈당 (mg/dL) |
| `peakMgdl` | Double | 예측 최고 혈당 (mg/dL) |
| `peakMinute` | Integer | 최고 혈당 도달 시간 (분) |
| `returnMinute` | Integer | 목표 범위 복귀 소요 시간 (분) |
| `modelType` | String | 사용된 모델 — `generic` 또는 `personalized` |
| `confidence` | Double | 예측 신뢰도 (0.0 ~ 1.0) |

---

## 5. 합의 필요 사항

### 5-1. curve 시계열 간격

FE 정밀 시뮬레이터에서 차트를 렌더링해야 하므로 포인트 간격이 중요합니다.

| 옵션 | 포인트 수 | 장점 | 단점 |
|------|-----------|------|------|
| **5분 간격** (0, 5, 10, ..., 120) | 25개 | 곡선이 부드러움 | 응답 크기 ↑, 추론 부담 |
| **15분 간격** (0, 15, 30, ..., 120) | 9개 | 가벼움, CGM 측정 주기와 유사 | 피크 시점 오차 ±7분 |
| **가변 간격** (피크 전후만 촘촘) | 10~15개 | 효율적 | FE 보간 로직 필요 |

> **BE 제안**: 5분 간격 25개 포인트. FE 차트 부드러움 + 피크 시점 정확도 확보.
> **질문**: AI 모델이 실제로 어떤 단위로 예측하는지? 모델 출력을 리샘플링하는 게 나은지?

### 5-2. 모델 전환 기준 및 필수 요건 ✅ 확정

유저별 데이터 보유 수준에 따른 모델 분기:

| 유저 유형 | 보유 데이터 | 사용 모델 | currentGlucose 처리 |
|-----------|------------|-----------|-------------------|
| 신규 (CGM 없음) | diabetesType, isMedicated | generic | 기본값 100mg/dL 사용 |
| CGM 연동 초기 | + currentGlucose | generic | 실측값 사용 (더 정확) |
| CGM 패턴 축적 (14일+) | + riseRate, fallRate, spikeThreshold | personalized | 실측값 필수 |

> **확정**: generic은 `food` 필수 필드 4종 + `diabetesType` + `isMedicated`만으로 예측 가능. `currentGlucose`가 없으면 기본값(100mg/dL)으로 대체.
> **AI 파트 확인 필요**: personalized 전환 임계 조건 (cgm_patterns.response_count >= N 또는 personalized_at != null)

### 5-3. modelType 선택 주체

| 방식 | 설명 |
|------|------|
| **A) AI가 자동 선택** | AI가 입력 데이터를 보고 generic/personalized 판단, 응답에 사용된 modelType 포함 |
| **B) BE가 명시** | BE가 요청에 `modelType` 필드를 추가하여 지정 |

> **BE 제안**: 방식 A (AI 자동 선택). BE는 가진 데이터를 모두 보내고, AI가 판단.

### 5-4. 응답 지연 시간

완료 기준: **응답 2초 이내 (캐시 적중 시 500ms 이내)**

| AI 추론 시간 | BE 대응 |
|-------------|---------|
| **< 2초** | 동기 호출 유지 (현재 구현, readTimeout=5초) |
| **2~10초** | 비동기 전환 검토 (202 Accepted → 폴링 or SSE) |
| **> 10초** | 비동기 필수 + 진행 상태 알림 |

> **질문**: 현재 모델 추론에 예상되는 시간은? GPU 사용 여부에 따른 차이는?

### 5-5. AI 에러 응답 포맷

BE에서 에러 유형별 처리를 위해 AI 에러 응답 포맷 통일이 필요합니다.

**제안 포맷:**

```json
{
  "error": "insufficient_data",
  "message": "giScore is required for prediction",
  "detail": {
    "missingFields": ["giScore"]
  }
}
```

**에러 유형 제안:**

| HTTP Status | error 코드 | 상황 |
|-------------|-----------|------|
| `422` | `insufficient_data` | 필수 입력 필드 부족 |
| `422` | `invalid_input` | 입력값 범위 초과 (예: 음수 칼로리) |
| `500` | `model_error` | 모델 추론 실패 |
| `503` | `model_loading` | 모델 로딩 중 (서버 시작 직후) |

> **질문**: `422`와 `400` 중 어떤 것을 입력 오류로 사용할지?

### 5-6. A/B 비교 배치 요청

스토리에 "A/B 비교 요청(2개 음식 동시 입력)" 지원이 포함되어 있습니다.

| 방식 | AI 쪽 변경 | 장점 | 단점 |
|------|-----------|------|------|
| **A) BE에서 병렬 호출 2회** | 없음 | 구현 단순, AI 변경 불필요 | 네트워크 비용 2배 |
| **B) AI 배치 엔드포인트** | `POST /inference/glucose/batch` 추가 | 모델 로딩 1회로 효율적 | AI 구현 추가 필요 |

> **BE 제안**: 초기엔 방식 A (병렬 2회)로 구현하고, 성능 이슈 발생 시 방식 B로 전환.
> **질문**: 모델 로딩 오버헤드가 요청마다 발생하는지, 서버 시작 시 1회만 로딩하는지?

---

## 6. BE 구현 현황

| 항목 | 상태 | 파일 |
|------|------|------|
| 요청/응답 DTO | ✅ 완료 | `domain/prediction/client/dto/` |
| AI 클라이언트 인터페이스 | ✅ 완료 | `GlucosePredictClient.java` |
| RestClient 구현체 | ✅ 완료 | `GlucosePredictClientImpl.java` |
| 에러 예외 클래스 | ✅ 완료 | `AiServiceException.java` |
| 설정 (URL, 타임아웃) | ✅ 완료 | `AiServiceProperties.java`, `application.yml` |
| 예측 컨트롤러/서비스 | ⬜ 대기 | 인터페이스 합의 후 구현 예정 |
| 캐싱 | ⬜ 대기 | 캐시 키 전략 합의 후 구현 예정 |

---

## 7. 다음 단계

1. **AI 파트 리뷰** → 위 합의 사항 6개 항목에 대해 의견 회신
2. **인터페이스 확정** → 합의 내용 반영하여 DTO 스키마 최종 확정
3. **BE 컨트롤러/서비스 구현** → `POST /api/predict/glucose` 엔드포인트 구현
4. **통합 테스트** → AI 서버 mock으로 BE 단위 테스트 → AI 서버 연동 통합 테스트
