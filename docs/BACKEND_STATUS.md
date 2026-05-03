# 백엔드 구현 현황 진단 (vs ERD + 기획안)

> **분석 기준일**: 2026-05-01
> **브랜치**: `fe/feature-glucofit-integration-S14P31S309-698`
> **분석 대상**:
> - 기획안: `glucocoach_feature_detail.html` (10개 기능)
> - ERD: `real_end(MVP).sql` (16개 테이블)
> - 백엔드: `backend/src/main/java/com/ssafy/s309/**`

---

## 📋 한 줄 결론

> **"베이스 인프라(JWT / FCM / S3 / AI 클라이언트)는 완성도 높음. 그러나 도메인 절반(혈당수신·식사기록·알림·성적표·주간보고서)이 트리거조차 없는 상태."**
>
> 다음 핵심 작업은 *새 인프라 깔기*가 아니라 *이미 깔린 인프라를 사용하는 도메인 로직 끼우기*.

---

## 🗄️ 1. DB 스키마

> 2026-05-02 헬스 도메인 리팩터로 마이그레이션이 V1~V3으로 단순화됨.
> sleep_records / exercise_records는 V2에서 DROP, daily_health_summaries로 통합.

| 테이블 | 마이그레이션 | 엔티티 | 상태 |
|---|---|---|---|
| users | V1 | `User` | ✅ |
| ward_guardian | V1 | `WardGuardian` | ✅ |
| notification_tokens | V1 | `NotificationToken` | ✅ |
| glucose_predictions | V1 | `GlucosePrediction` | ✅ |
| glucose_records | V1 | `GlucoseRecord` | ✅ (테이블·엔티티만) |
| meal_records | V1 | `MealRecord` | ✅ (테이블·엔티티만) |
| foods | V1 | `Food` | ✅ |
| daily_health_summaries | **V2** | `DailyHealthSummary` | ✅ **(API 구현 완료, 944 PR)** |
| health_snapshots | **V3** | `HealthSnapshot` | ✅ **(wide-format 시계열, 948 PR — steps/calories/heart_rate)** |
| **alerts** | ❌ | ❌ | **누락** |
| **guardian_notifications** | ❌ | ❌ | **누락** |
| **meal_glucose_responses** | ❌ | ❌ | **누락** (식후 추적의 핵심) |
| **user_food_grades** | ❌ | ❌ | **누락** (성적표의 핵심) |
| **weekly_reports** | ❌ | ❌ | **누락** |
| **weekly_foods** | ❌ | ❌ | **누락** |
| **medications_records** | ❌ | ❌ | **누락** |

### 마이그레이션 진행 흐름

```
V1: init schema (인증 + 예측 + glucose/meal/foods 등 팀 공통 V1으로 통합 가정)
V2: sleep_records, exercise_records DROP → daily_health_summaries 신설 (1분 폴링 upsert)
V3: health_snapshots 추가 (5분 batch append, 1분 polling 메트릭 4종 wide-format 시계열)
```

> 알림·식후추적·성적표·주간보고서 도메인은 여전히 미착수.

---

## 📡 2. 기능 ①~⑩ 구현 매트릭스

| # | 기능 | 스펙의 핵심 API/메커니즘 | 현 백엔드 | 상태 |
|---|---|---|---|---|
| ① | **회원가입** | `POST /api/v1/auth/signup` (14필드) + FCM 토큰 + 보호자 등록 통합 | `POST /api/auth/signup` (email+password 2필드) + 별도 `PUT /settings` + 별도 `PUT /fcm-token` + 별도 `POST /guardians` | 🟡 **3-step 분리** — 스펙은 단일 가입, 실제는 4번 호출 필요 |
| ② | **로그인** | email + 카카오 소셜 + refresh + logout | `/api/auth/login·/refresh·/logout·/withdraw` ✅, `/auth/social` ❌ | 🟡 **이메일만** — 카카오 소셜 미구현 |
| ③ | **대시보드** | `GET /api/v1/dashboard` 통합(현재혈당+TIR+식사+알림+보고서) | `GET /api/timeline?range=1d/7d/30d` (혈당+식사/운동/수면 이벤트만) | 🟡 **변형 구현** — TIR·알림뱃지·grade·보고서 한줄 미포함 |
| ④ | **혈당 수신** | `POST /api/v1/glucose-records` (5분 주기) + Spring Event + HIGH/LOW 트리거 | `GlucoseRecord` 엔티티+레포만, **컨트롤러 없음** | 🔴 **미구현** — CGM 데이터 인입 경로 자체가 없음 |
| ⑤ | **알림** | alerts 테이블 + HIGH/LOW/SOS/WEEKLY_REPORT FCM + 에스컬레이션 + 목록/읽음 | `PUT /fcm-token` (토큰 등록뿐) | 🔴 **거의 미구현** — alerts 도메인 전체 부재 |
| ⑥ | **식사 예측** | `/predictions/image`(이미지+AI인식+S3) + `/predictions/text` + Bergman 시뮬 | `POST /api/predict/glucose` (텍스트만) + `GlucosePredictClient`(AI 호출) | 🟡 **텍스트만** — 이미지·S3·AI 음식인식 부재 |
| ⑦ | **음식 비교** | `POST /predictions/compare` + 병렬 AI | `POST /api/predict/glucose/compare` (CompletableFuture 병렬) | 🟢 **구현** |
| ⑧ | **식사 기록** | `POST /meal-records` + S3 + 식후 2.5h 스케줄러 + slope 계산 | 엔티티+레포만, 컨트롤러/서비스/스케줄러 부재 | 🔴 **미구현** (테이블만) |
| ⑨ | **음식 성적표** | `GET /food-grades` + UPSERT + S/A/B/C/D | 전부 없음 | 🔴 **미구현** |
| ⑩ | **주간 보고서** | 매일 00:05 스케줄러 + LLM + iText7 PDF + S3 + FCM | 전부 없음 | 🔴 **미구현** |

### 부수 구현
- `GET /api/foods/search` — 식품안전처 API + DB 캐시 (스펙 ⑥의 자동완성에 해당)
- `GET/PUT /api/users/{userId}/settings` — 사용자 설정 CRUD (스펙엔 명시 없으나 가입 보완용)
- `Auth /api/auth/withdraw` — 회원 탈퇴 (소프트 삭제 + 익명화)
- `POST /api/health/daily-summary` (944 PR) — 일별 헬스 요약 upsert (대시보드 + Agent 일별 조회)
- `GET /api/health/daily-summary?from=&to=` (944 PR) — 기간 조회
- `POST /api/health/snapshots` (948 PR) — 1분 폴링 메트릭(steps/calories/HR) 5분 batch INSERT (멱등, ON CONFLICT)
- `GET /api/agent/steps` (948 PR) — Agent 전용, MAX-MIN 윈도우 쿼리, X-Agent-Api-Key 인증
- `GET /api/agent/user-profile` (948 PR, **#971/#976/#977**) — Agent #1, users 프로필(diabetes_type/target_low/target_high)
- `GET /api/agent/glucose` (948 PR, **#972/#978/#979/#980**) — Agent #2, 시계열 혈당 raw
- `GET /api/agent/sleep` (948 PR, **#973/#981/#982/#983**) — Agent #3, 일별 sleep + 7일 평균
- `GET /api/agent/meals` (948 PR, **#974/#984/#985/#986/#987**) — Agent #4, 일별 식사 + 영양 (theta join)

### Agent API 진행 상황 (총 8개 명세, 6개 구현 + 2개 보류)

| # | 엔드포인트 | 상태 |
|---|---|---|
| 1 | GET /api/agent/user-profile | ✅ 948 |
| 2 | GET /api/agent/glucose | ✅ 948 |
| 3 | GET /api/agent/sleep | ✅ 948 |
| 4 | GET /api/agent/meals | ✅ 948 |
| 5 | GET /api/agent/steps | ✅ 948 |
| 6 | GET /api/agent/notifications | 🔴 alerts 도메인 의존 (M2) |
| 7 | POST /api/agent/notifications | 🔴 alerts 도메인 의존 (M2) |
| 8 | schedule_followup | ⏸ 메커니즘 별도 설계 (`agent_pending_triggers` 큐, M2) |

---

## 🎯 3. 가장 중요한 깊이 발견 3가지

### 발견 ① — 인프라는 다 살아있는데 "트리거"가 없어서 죽어있음

| 인프라 | 위치 | 상태 |
|---|---|---|
| **FcmService** (`FirebaseMessaging.send()` 실제 호출) | `common/service/FcmService.java` | ✅ 완성 |
| **NotificationTokenService.sendAlert()** | `notification/service/NotificationTokenService.java:34` | ✅ 정의됨, **호출하는 곳 0곳** (dead code) |
| **S3Service** (upload / presigned GET·PUT / delete) | `common/service/S3Service.java` | ✅ 완성, **사용처 0곳** |
| **FirebaseConfig** (`firebase-service-account.json` 로드) | `config/FirebaseConfig.java` | ✅ 완성 |
| **S3Config** (IAM credentials + endpoint override) | `config/S3Config.java` | ✅ 완성 (LocalStack 호환) |
| **GlucosePredictClientImpl** (재시도 3회·MDC 상관관계ID·타임아웃) | `prediction/client/GlucosePredictClientImpl.java` | ✅ Production-ready |

> ⑤알림·⑥이미지예측·⑧식사기록이미지·⑩주간보고서PDF는 **인프라 0에서 만드는 게 아니다**. 도메인 트리거(혈당 임계 감지·이미지 업로드 호출)만 끼우면 즉시 동작.

---

### 발견 ② — `@Scheduled` / `@Async` 단 한 줄도 없음

```bash
$ grep -r "@Scheduled|@Async|@EnableScheduling|@EnableAsync" backend/src/main/java
→ No matches found
```

다음 기능들이 모두 비동기/스케줄링 인프라 활성화에 의존:
- ⑧ 식후 2.5h 추적 (`@Scheduled`)
- ⑤ SOS 에스컬레이션 5분 주기 (`@Scheduled`)
- ⑩ 주간보고서 매일 00:05 (`@Scheduled` + `@Async`)
- ④ Spring Event 비동기 알림 (`@TransactionalEventListener(AFTER_COMMIT)`)

> **선결 작업: `BackendApplication`에 `@EnableScheduling` + `@EnableAsync` + `AsyncConfig` Executor 빈 정의**

---

### 발견 ③ — 회원가입이 사실상 email + password만 받음 (스펙과 큰 괴리)

`backend/src/main/java/com/ssafy/s309/domain/auth/dto/SignupRequest.java`:
```java
public record SignupRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min=6) String password) {}
```

`AuthService.signup()`:
```java
User user = User.builder()
    .email(request.email())
    .password(passwordEncoder.encode(request.password()))
    .provider("email")
    .build();   // ← name/age/gender/phone/height/weight/diabetes_type 등 전부 빠짐
```

**스펙 요구 14필드** vs **실제 수용 2필드**:
- name, age, gender, phone, height, weight
- diabetes_type, is_medicated, target_low, target_high, hba1c_manual
- fcm_token, device_type
- → 모두 미수용

**현재 동작 패턴 (4-step):**
```
1) POST /api/auth/signup           (email + password만)
2) PUT  /api/users/{id}/settings   (11개 필드 사후 채우기)
3) PUT  /api/users/{id}/fcm-token  (FCM 토큰 별도 등록)
4) POST /api/users/{id}/guardians  (보호자 별도 등록, 선택)
```

> 프론트와 합의 안 됐으면 즉시 정렬 필요. 옵션 (A) `SignupRequest` 확장해서 단일 가입 / (B) 현재 4-step 패턴을 스펙에 반영.

---

## 🔍 4. 도메인별 깊은 진단

### 4.1 회원가입/로그인 (①②)

| 항목 | 위치 | 메모 |
|---|---|---|
| `AuthService.signup()` | `auth/service/AuthService.java:26` | email+password만 처리. **ERD `users.name NOT NULL`인데 V1 마이그레이션은 `name NULL`** — 마이그레이션이 ERD를 어김 |
| 카카오 소셜 | — | 흔적 0 |
| `AuthController` | `auth/controller/AuthController.java` | signup/login/refresh/logout/withdraw 5개 endpoint 정상 |

### 4.2 예측 (⑥⑦) — 가장 정교하게 구현된 도메인

| 항목 | 위치 | 메모 |
|---|---|---|
| `PredictionService.buildAiRequest()` | `prediction/service/PredictionService.java:90` | **`currentGlucose: null  // CGM 미구현`** 코멘트 박혀있음 → CGM 컨트롤러 없어서 baseline 항상 null |
| `GlucosePredictClientImpl` | `prediction/client/GlucosePredictClientImpl.java` | RestClient 재시도(3회)·타임아웃·MDC 상관관계 ID — **production-ready 수준** |
| `comparePredict` | `prediction/service/PredictionService.java:42` | `CompletableFuture.supplyAsync` 병렬 (`@Async` 없이 ForkJoinPool 기본값 사용) |
| `PredictResponse.java` | `prediction/dto/PredictResponse.java:6` | **6번줄에 비정상적으로 긴 공백** (record 키워드와 클래스명 사이). git status M으로 표시되는 그 파일. 즉시 정리 필요 |

### 4.3 식사 기록 (⑧)

| 항목 | 위치 | 메모 |
|---|---|---|
| `MealRecord` 엔티티 | `meal/entity/MealRecord.java` | `userId, foodId, imageOriginName, imageStorageKey, isProcessed, recordedAt` — **6필드뿐** |
| **스펙/ERD에 있는 `memo` 컬럼이 V4 마이그레이션에서 누락** | `db/migration/V4__add_timeline_tables.sql:30-41` | ERD엔 있는데 SQL엔 없음 → 추가 마이그레이션 필요 |
| 미구현 필드 | — | `amount_g`, `carb_g`, `slope`, `grade` 등 핵심 필드 부재 |
| 컨트롤러/서비스/스케줄러 | — | 모두 없음 |

### 4.4 알림 (⑤)

- `alerts` 테이블·엔티티·트리거 모두 없음
- 다만 `NotificationTokenService.sendAlert(user, title, body)` 메서드는 이미 정의 → **`AlertTriggerService`를 만들어서 호출만 하면 됨**

### 4.5 보안/권한 — 잠재 이슈 ⚠️

`SecurityConfig`는 JWT 필터만 적용. **`UserController`/`NotificationController`가 path에 `{userId}` 받지만, JWT principal과 path userId 비교 코드 없음.**

```java
// NotificationController.java:24
@PutMapping
public ResponseEntity<Void> saveToken(
    @PathVariable Long userId,         // ← path에서 userId 받음
    @Valid @RequestBody FcmTokenRequest request) {
  userRepository.findById(userId)      // ← principal과 비교 없음
      .ifPresent(user -> ...);
}
```

> A 사용자가 자신의 토큰으로 **B의 설정/FCM 토큰/보호자 조작 가능**. 시연 전 권한 체크 필수.

### 4.6 기타 코드 정합성

| 항목 | 위치 | 메모 |
|---|---|---|
| stale TODO | `BackendApplication.java:14-16` | "도메인 패키지 구조 / SecurityConfig / JWT 인증" — **이미 다 구현된 항목**의 코멘트만 남음 |
| `Guardian` 엔티티 dead code 의심 | `user/entity/Guardian.java` | `WardGuardian`만 사용됨, `Guardian`은 어디서도 참조 안 됨 |
| `GlucosePrediction.id` 타입 불일치 | `prediction/entity/GlucosePrediction.java:35` | `Integer` (다른 모든 엔티티는 `Long`) |
| `TimelineService` | `timeline/service/TimelineService.java` | 4쿼리를 CompletableFuture로 병렬화. 단 통계(TIR/avg/min/max) 계산 없이 raw 시계열 + 이벤트만 반환 |

---

## 🧹 5. 즉시 정리 가능한 사소한 문제 (PR 한 번에 묶기 좋음)

| # | 항목 | 위치 |
|---|---|---|
| 1 | `PredictResponse.java` 6번줄 거대 공백 정리 | `prediction/dto/PredictResponse.java:6` |
| 2 | `BackendApplication` stale TODO 코멘트 3개 삭제 | `BackendApplication.java:14-16` |
| 3 | V1 SQL의 `name NULL` → `name NOT NULL` (ERD와 정렬) | `db/migration/V1__init_schema.sql:13` |
| 4 | V4 SQL `meal_records.memo VARCHAR(255)` 컬럼 추가 (ERD와 정렬) | `db/migration/V4__add_timeline_tables.sql:30` |
| 5 | `Guardian` 엔티티 dead code 판단 후 삭제 또는 명시적 사용 | `user/entity/Guardian.java` |
| 6 | `glucocoach_feature_detail.html` 1556~1992줄 중복 / 깨진 텍스트 정리 | 프로젝트 루트 |
| 7 | URL prefix 통일 (`/api/...` vs 스펙 `/api/v1/...`) | 컨벤션 결정 후 일괄 변경 |

---

## 🚀 6. 다음 작업 우선순위 (의존 그래프 + 인프라 활용 고려)

```
[Phase A — 인프라 활성화 · 1일]
  1) BackendApplication에 @EnableScheduling + @EnableAsync
  2) AsyncConfig (Executor 빈 정의)
  3) SignupRequest 확장 + AuthService 재작성 (스펙 14필드 수용)
     ↳ 또는 프론트와 합의해서 "현재 3-step 패턴 유지" 결정

[Phase B — CGM 인입 · 0.5일]      ← 모든 후속의 entry point
  4) GlucoseRecordController POST + Spring Event 발행
  5) GlucoseRecord 기간조회 API
     ↳ FCM 인프라 살아있으니, 이때 같이 ⑤ HIGH/LOW 알림 트리거 묶어도 됨

[Phase C — 식사기록 + 식후 추적 · 2일]
  6) meal_records 스키마 보강 마이그레이션 (memo, amount_g, carb_g 등)
  7) meal_glucose_responses 테이블 + 엔티티
  8) MealRecordController POST/GET (S3Service 활용)
  9) GlucoseResponseTracker @Scheduled 구현

[Phase D — 자동 갱신 · 1일]
 10) user_food_grades 테이블 + UPSERT
 11) FoodGradeService — meal_glucose_responses 생성 시 자동 호출

[Phase E — 알림 발생부 · 1일]
 12) alerts 테이블 + 엔티티
 13) AlertTriggerService (HIGH/LOW만 우선) → 이미 있는 sendAlert() 호출

[Phase F — 큰 작업 (선택) · 3일+]
 14) 카카오 소셜
 15) 이미지 입력 식사예측 (S3 + AI 인식)
 16) 주간보고서 (LLM + iText7 + 스케줄러)
 17) SOS 에스컬레이션
```

### 🎯 ROI 추천: Phase B + 한쪽 알림 트리거

인프라(JWT/FCM/AI) 다 있으므로 한 번에 **"혈당 들어옴 → DB 저장 → HIGH/LOW 알림 발사"**까지 0.5~1일이면 시연 가능 수준 도달.

---

## 📂 부록 — 백엔드 도메인 구조 한눈에

```
backend/src/main/java/com/ssafy/s309/
├── BackendApplication.java                  ⚠️ stale TODO 3개
├── common/
│   ├── entity/BaseEntity.java
│   ├── exception/GlobalExceptionHandler.java
│   └── service/
│       ├── FcmService.java                  ✅ 완성, 활용처 1곳뿐
│       └── S3Service.java                   ✅ 완성, 활용처 0곳
├── config/
│   ├── AiClientConfig.java
│   ├── AiServiceProperties.java
│   ├── FirebaseConfig.java                  ✅ 완성
│   ├── FoodApiClientConfig.java
│   ├── FoodApiProperties.java
│   ├── JpaConfig.java
│   ├── RedisConfig.java
│   ├── S3Config.java                        ✅ 완성
│   └── SecurityConfig.java                  ⚠️ path userId 권한 검증 없음
└── domain/
    ├── auth/                                🟡 카카오 소셜 부재, signup 2필드만
    ├── cgm/                                 🔴 컨트롤러 없음 (entity+repo만)
    ├── food/                                🟢 식품안전처 API 캐시 완성
    ├── health/                              🟡 entity+repo만 (timeline 통해 노출)
    ├── meal/                                🔴 컨트롤러 없음 (entity+repo만)
    ├── notification/                        🟡 토큰 등록만, 알림 발생 부재
    ├── prediction/                          🟢 텍스트 단일/A-B 비교 완성, 이미지 부재
    ├── timeline/                            🟢 통합 조회 (대시보드 변형)
    └── user/                                🟢 settings + guardians CRUD 완성
```

---

## 변경 이력

- 2026-05-01 — 초기 작성. ERD 16개 / 백엔드 도메인 9개 / 마이그레이션 V1~V6 / 컨트롤러 6개 / 서비스 단 깊이 분석 완료.