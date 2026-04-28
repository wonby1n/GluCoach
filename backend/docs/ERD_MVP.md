# ERD MVP 설계서 (ERD Cloud용)

> **작성일:** 2026-04-27
> **기준:** ERD 회의록(1·2차) + API_SPEC.md (🔴 Highest / 🟠 High)
> **원칙:** FK는 최소화, cgm_logs와 다른 테이블은 시간 기준 조회 (회의 결론)

---

## 테이블 목록 한눈에 보기

| # | 테이블 | 역할 | 우선순위 | 구현 상태 |
|---|--------|------|---------|----------|
| 1 | users | 회원 정보 + 설정 | 🔴 | 완료 |
| 2 | guardians | 보호자 (SOS 발송 대상) | 🟠 | 완료 |
| 3 | cgm_logs | 혈당 원시 데이터 (핵심) | 🔴 | 미구현 |
| 4 | meal_logs | 식사 기록 | 🔴 | 미구현 |
| 5 | meal_glucose_responses | 식후 혈당 반응 + 성적 | 🟠 | 미구현 |
| 6 | foods | 음식 영양정보 캐시 | 🔴 | 미구현 |
| 7 | glucose_predictions | 식전 예측 결과 | 🔴 | 미구현 |
| 8 | notification_tokens | FCM 푸시 토큰 | 🟠 | 미구현 |
| 9 | alerts | 혈당 알림 기록 | 🔴 | 미구현 |

---

## 1. users (회원)

> **메모:** 회원가입 시 이메일/비밀번호 또는 소셜(카카오/구글) 가입.
> 온보딩에서 신체정보·당뇨유형·목표범위 수집.
> `deleted_at`이 NOT NULL이면 탈퇴 회원 (소프트 삭제).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **user_id** | UUID | PK | |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 탈퇴 시 `deleted_{uuid}@withdrawn.local`로 변경 |
| password | VARCHAR(255) | NULLABLE | 소셜 로그인 시 NULL |
| provider | VARCHAR(32) | NOT NULL, DEFAULT 'email' | `email` / `kakao` / `google` |
| height | FLOAT | NULLABLE | 키 (cm) |
| weight | FLOAT | NULLABLE | 체중 (kg) |
| diabetes_type | VARCHAR(10) | NOT NULL, DEFAULT 'NONE' | `NONE` / `TYPE1` / `TYPE2` |
| is_medicated | BOOLEAN | NOT NULL, DEFAULT false | 당뇨약/인슐린 복용 여부 |
| target_low | INTEGER | NOT NULL, DEFAULT 70 | 목표 혈당 하한 (mg/dL) |
| target_high | INTEGER | NOT NULL, DEFAULT 140 | 목표 혈당 상한 (mg/dL) |
| alert_low | INTEGER | NOT NULL, DEFAULT 70 | 저혈당 알림 기준 |
| alert_high | INTEGER | NOT NULL, DEFAULT 180 | 고혈당 알림 기준 |
| night_watch | BOOLEAN | NOT NULL, DEFAULT false | 야간 모니터링 ON/OFF |
| character_type | VARCHAR(32) | NOT NULL, DEFAULT 'BASIC' | 아바타 캐릭터 타입 |
| deleted_at | TIMESTAMP | NULLABLE | 탈퇴 시각 (소프트 삭제) |
| created_at | TIMESTAMP | NOT NULL | 가입일 (자동) |
| updated_at | TIMESTAMP | NOT NULL | 수정일 (자동) |

---

## 2. guardians (보호자)

> **메모:** SOS 발송 대상. `priority` 순서대로 연락.
> 삭제 시 나머지 priority 자동 재정렬.
> 1명의 유저가 여러 보호자 등록 가능.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **guardian_id** | UUID | PK | |
| user_id | UUID | FK → users, NOT NULL | 보호자 소유 유저 |
| name | VARCHAR(64) | NOT NULL | 보호자 이름 |
| phone | VARCHAR(20) | NOT NULL | 연락처 |
| relation | VARCHAR(32) | NULLABLE | 관계 (부모, 배우자 등) |
| is_primary | BOOLEAN | NOT NULL, DEFAULT false | 대표 보호자 여부 |
| priority | INTEGER | NOT NULL, DEFAULT 0 | SOS 연락 순서 (0부터) |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:** `idx_guardian_user (user_id)`

---

## 3. cgm_logs (혈당 측정)

> **메모:** 전체 서비스의 핵심 테이블.
> 대시보드 혈당 그래프의 데이터 소스.
> 다른 테이블과 FK 없음 — `measured_at` 시간 범위로 조회.
> BLE SDK → 앱 → POST `/api/cgm/sync` → 이 테이블에 적재.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **cgmlog_id** | UUID | PK | (회의: reading_id → cgmlog_id 변경) |
| user_id | UUID | FK → users, NOT NULL | 측정 대상 유저 |
| glucose_value | FLOAT | NOT NULL | 혈당값 (mg/dL) |
| measured_at | TIMESTAMP | NOT NULL | 실제 측정 시각 |
| created_at | TIMESTAMP | NOT NULL | 서버 수신 시각 |

**인덱스:**
- `idx_cgm_user_time (user_id, measured_at)` — 시간 범위 조회 최적화 (가장 빈번한 쿼리)

> **회의 결론 반영:**
> - `trend` 컬럼 제거 → 프론트에서 계산
> - `source` 컬럼 제거 → BLE만 사용
> - updated_at 불필요 (측정값은 수정 안 함)

---

## 4. meal_logs (식사 기록)

> **메모:** 식사를 기록하면 이 시점부터 2시간 동안의 cgm_logs를 시간 기준으로 조회.
> 즉, 이 테이블이 "혈당 반응 분석의 트리거" 역할.
> cgm_logs와 FK 없음 — recorded_at 기준 시간 조회.
> 음식 영양정보는 foods 테이블에서 참조.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **meal_id** | UUID | PK | |
| user_id | UUID | FK → users, NOT NULL | |
| food_id | BIGINT | FK → foods, NULLABLE | 매칭된 음식 (수동 입력 시 NULL 가능) |
| food_name | VARCHAR(100) | NOT NULL | 음식명 (표시용) |
| raw_input | TEXT | NULLABLE | 원본 입력값 보존 (사진 URL, 텍스트 등) |
| peak_glucose | FLOAT | NULLABLE | 식후 2시간 내 최고 혈당 (나중에 채워짐) |
| recorded_at | TIMESTAMP | NOT NULL | 식사 시각 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:**
- `idx_meal_user_time (user_id, recorded_at)`

> **회의 결론 반영:**
> - `input_method` 제거 → 음성 입력 미지원
> - `simulated` 제거 → 예측은 glucose_predictions 테이블로 분리
> - 사진 컬럼 제거 → raw_input에 URL로 보존
> - diary 방식 철회 → meal_logs 유지

---

## 5. meal_glucose_responses (식후 혈당 반응)

> **메모:** 식사 후 2시간 동안의 혈당 반응을 요약·평가.
> 기울기(slope)로 등급 산출: 상승이 완만할수록 좋은 등급.
> 음식 성적표 조회 API의 데이터 소스.
> meal_logs 1건당 response 1건 (1:1 관계).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **response_id** | UUID | PK | |
| meal_id | UUID | FK → meal_logs, NOT NULL, UNIQUE | 1:1 |
| user_id | UUID | FK → users, NOT NULL | 조회 편의 |
| baseline_glucose | FLOAT | NULLABLE | 식전 혈당 (기준점) |
| peak_glucose | FLOAT | NULLABLE | 2시간 내 최고 혈당 |
| slope | FLOAT | NULLABLE | 상승 기울기 (mg/dL/min) — 등급 산출 기준 |
| return_time | INTEGER | NULLABLE | 목표 혈당 복귀까지 걸린 시간 (분) |
| grade | VARCHAR(2) | NULLABLE | `S` / `A` / `B` / `C` / `D` |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:**
- `idx_response_user (user_id)` — 유저별 성적표 조회

> **등급 기준 (API 스펙 기준 — 피크값 기반):**
> S: 개인 목표 범위 이내 / A: ~140 / B: 141~170 / C: 171~200 / D: 201+
> ※ 최종 기준은 기울기 vs 피크값 중 추후 확정 (미결 사항)

---

## 6. foods (음식 영양정보)

> **메모:** 식품안전처 공공데이터 API 조회 결과를 캐싱.
> 한 번 조회된 음식은 DB에 저장 → 다음부터 API 호출 없이 반환.
> AI 음식 인식 결과도 이 테이블에 매핑.
> 예측 API 호출 시 영양정보(탄수화물, 단백질, 지방 등)를 여기서 가져감.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **food_id** | BIGINT | PK, AUTO_INCREMENT | |
| food_name | VARCHAR(200) | NOT NULL | 음식명 |
| calories | FLOAT | NULLABLE | 칼로리 (kcal) |
| carbohydrate | FLOAT | NULLABLE | 탄수화물 (g) |
| protein | FLOAT | NULLABLE | 단백질 (g) |
| fat | FLOAT | NULLABLE | 지방 (g) |
| sugar | FLOAT | NULLABLE | 당류 (g) |
| fiber | FLOAT | NULLABLE | 식이섬유 (g) |
| gi_index | FLOAT | NULLABLE | 혈당지수 (GI) |
| serving_size | VARCHAR(50) | NULLABLE | 1회 제공량 (예: "100g") |
| source | VARCHAR(50) | NOT NULL, DEFAULT 'PUBLIC_API' | 데이터 출처 |
| created_at | TIMESTAMP | NOT NULL | |

**인덱스:**
- `idx_food_name (food_name)` — 음식명 검색

---

## 7. glucose_predictions (식전 예측)

> **메모:** "이걸 먹으면 혈당이 어떻게 될까?" 시뮬레이션 결과 저장.
> 식전에 AI 모델이 예측한 혈당 곡선.
> 식후 실측 데이터(meal_glucose_responses)와 비교하여 정확도 평가 가능.
> A/B 비교 모드: 같은 시점에 2개 음식 예측 → pair_id로 묶음.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **prediction_id** | UUID | PK | |
| user_id | UUID | FK → users, NOT NULL | |
| food_id | BIGINT | FK → foods, NULLABLE | 예측 대상 음식 |
| food_name | VARCHAR(100) | NOT NULL | 음식명 (표시용) |
| predicted_curve | JSONB | NOT NULL | 예측 혈당 곡선 `[{time, value}, ...]` |
| predicted_peak | FLOAT | NULLABLE | 예측 최고 혈당 |
| model_type | VARCHAR(20) | NOT NULL, DEFAULT 'GENERIC' | `GENERIC` / `PERSONALIZED` |
| pair_id | UUID | NULLABLE | A/B 비교 시 같은 pair_id 공유 |
| created_at | TIMESTAMP | NOT NULL | 예측 시각 |

**인덱스:**
- `idx_prediction_user_time (user_id, created_at)`
- `idx_prediction_pair (pair_id)` — A/B 비교 조회

> **회의 결론:** 예측값 별도 저장 불필요라고 했으나,
> API 스펙에 `glucose_predictions` 테이블이 명시되어 있고
> A/B 비교·정확도 역산 기능에 필요 → MVP에 포함.

---

## 8. notification_tokens (FCM 토큰)

> **메모:** 앱/워치에서 푸시 알림 수신을 위한 FCM 토큰.
> 로그아웃 시 `is_active = false` 처리.
> 디바이스 타입별 토큰 관리 (안드로이드 폰 / Galaxy Watch).

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **token_id** | BIGINT | PK, AUTO_INCREMENT | |
| user_id | UUID | FK → users, NOT NULL | |
| token | VARCHAR(512) | NOT NULL | FCM 토큰 값 |
| device_type | VARCHAR(20) | NOT NULL | `android` / `watch` |
| is_active | BOOLEAN | NOT NULL, DEFAULT true | 활성 여부 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

**인덱스:**
- `idx_noti_user_active (user_id, is_active)` — 활성 토큰 조회

---

## 9. alerts (알림 기록)

> **메모:** 고혈당(180+) / 저혈당(70-) 감지 시 서버에 기록.
> SOS 발송 이력도 이 테이블에 저장.
> 알림 발생 → 보호자 SOS 발송 → 스마트홈 트리거 체인의 시작점.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|---------|------|
| **alert_id** | UUID | PK | |
| user_id | UUID | FK → users, NOT NULL | |
| alert_type | VARCHAR(20) | NOT NULL | `HIGH` / `LOW` / `SOS` |
| glucose_value | FLOAT | NULLABLE | 감지 시점 혈당값 |
| message | TEXT | NULLABLE | 알림 메시지 내용 |
| latitude | DOUBLE | NULLABLE | GPS 위도 (SOS용) |
| longitude | DOUBLE | NULLABLE | GPS 경도 (SOS용) |
| resolved_at | TIMESTAMP | NULLABLE | 알림 해제 시각 |
| created_at | TIMESTAMP | NOT NULL | 알림 발생 시각 |

**인덱스:**
- `idx_alert_user_time (user_id, created_at)`

---

## 관계 (Relationships)

```
users (1) ──── (N) guardians           [FK: user_id]
users (1) ──── (N) cgm_logs            [FK: user_id]  ※ 시간 기준 조회 중심
users (1) ──── (N) meal_logs           [FK: user_id]
users (1) ──── (N) glucose_predictions [FK: user_id]
users (1) ──── (N) notification_tokens [FK: user_id]
users (1) ──── (N) alerts              [FK: user_id]

foods (1) ──── (N) meal_logs           [FK: food_id, NULLABLE]
foods (1) ──── (N) glucose_predictions [FK: food_id, NULLABLE]

meal_logs (1) ── (1) meal_glucose_responses [FK: meal_id, UNIQUE]
```

### 관계가 없는 것 (의도적)

| 관계 | 이유 |
|------|------|
| cgm_logs ↔ meal_logs | 시간 기준 조회 (FK 시 모든 CGM에 response 생겨 데이터 과잉) |
| cgm_logs ↔ meal_glucose_responses | 동일 이유 — 2시간치만 시간 범위 WHERE 절로 조회 |
| cgm_logs ↔ alerts | 알림은 온디바이스 감지 → 서버 기록. CGM FK 불필요 |

---

## 후순위 (MVP 이후 추가 예정)

| 테이블 | 시기 | 비고 |
|--------|------|------|
| weekly_reports | Sprint 3 | 주간 AI 리포트 (🟡 Medium) |
| medication_logs | Sprint 3 | 약물 복용 기록 (🟡 Medium) |
| cgm_patterns | 미정 | 회의에서 보류 결정. AI 학습 체계 확정 후 |
| smarthome_events | 미정 | 스마트홈 이벤트 (🟢 Low) |
| health_sync | 미정 | Samsung Health 연동 (🟡 Medium) |

---

## ERD Cloud 입력 가이드

### 테이블 생성 순서 (의존성 순)

1. `users` → 2. `guardians`, `cgm_logs`, `notification_tokens`, `alerts` (users만 참조)
3. `foods` → 4. `meal_logs` (users + foods 참조) → 5. `meal_glucose_responses` (meal_logs 참조)
6. `glucose_predictions` (users + foods 참조)

### 공통 컬럼 (BaseEntity)

모든 테이블에 `created_at`, `updated_at` 포함 (cgm_logs는 `updated_at` 제외).

### ERD Cloud 메모 복사용

각 테이블의 `> **메모:**` 부분을 ERD Cloud의 테이블 메모에 붙여넣으세요.
