# API 명세서

> **상태:** 진행 중 · **담당자:** 도현 · **업데이트:** 2026-05-06
> 통합 V1 마이그레이션 + ERD 정합성 작업 반영. backend/docs/ERD.sql 참조.

---

## 범례

| 표기 | 의미 |
|------|------|
| 🔴 Highest | MVP 필수, 최우선 구현 |
| 🟠 High | MVP 필수, 순차 구현 |
| 🟡 Medium | 2차 스프린트 |
| 🟢 Low | 시연용 또는 선택 기능 |
| 🟩 구현 완료 | BE 구현 + 테스트 통과 |
| ⬜ 미구현 | 미착수 |

---

## 1. 인증 (Auth)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 회원가입 | POST | `/api/auth/signup` | 이메일/소셜 가입. JWT access(1h) + refresh(30d) 발급. 회원가입 시 인증정보(email/password) + 개인정보(name/phone/age/gender) + 신체정보(height/weight) + 당뇨정보(diabetesType/isMedicated) + 목표혈당(targetLow/targetHigh) + 주간시작요일까지 한 번에 입력. | ⬜ | 🔴 Highest |
| 이메일 중복 확인 | GET | `/api/auth/email/check?email={email}` | 회원가입 전 이메일 사용 가능 여부 사전 조회. 인증 불필요. per-IP rate limit 10/min. 상세 명세는 [§1.1](#11-이메일-중복-확인-api-상세) | 🟩 | 🟠 High |
| 로그인 | POST | `/api/auth/login` | JWT Bearer 토큰 발급. 탈퇴 사용자(`deleted_at IS NOT NULL`) 차단. 사용자 열거 방지 메시지 통일 | 🟩 | 🔴 Highest |
| 토큰 갱신 | POST | `/api/auth/refresh` | access 만료 시 refresh로 재발급. Refresh Rotation 적용 (재사용 감지 시 모든 토큰 무효화) | 🟩 | 🔴 Highest |
| 로그아웃 | POST | `/api/auth/logout` | Redis의 RefreshToken 삭제 | 🟩 | 🔴 Highest |
| 회원 탈퇴 | DELETE | `/api/auth/withdraw` | 비밀번호 재검증 후 소프트 삭제(`deleted_at` 기록) + 이메일 익명화 + RefreshToken 삭제 | 🟩 | 🟠 High |
| 비밀번호 변경 | PUT | `/api/auth/password` | 로그인 사용자가 현재 비밀번호 검증 후 새 비밀번호로 교체. 성공 시 모든 기기의 RefreshToken 무효화. 상세는 [§1.3](#13-비밀번호-변경-api-상세) | 🟩 | 🟠 High |

### 1.1 이메일 중복 확인 API 상세

**요청**

```
GET /api/auth/email/check?email={email}
```

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | query string | △ | 검사할 이메일. 누락/공백/형식 위반 시 400 (`INVALID_FORMAT`). 정규식: `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` |

- 인증 헤더 불필요 (SecurityConfig 화이트리스트)
- 검증은 컨트롤러 내부 수동 처리 (`@Validated` 미사용 — 다른 엔드포인트로 가정 누수 방지)

**응답 스키마** — `EmailCheckResponse`

| 필드 | 타입 | 비고 |
|---|---|---|
| `available` | boolean | 사용 가능하면 true |
| `status` | enum | `AVAILABLE` / `ALREADY_REGISTERED` / `INVALID_FORMAT` / `RATE_LIMITED` |
| `retryAfterSeconds` | int? | `RATE_LIMITED` 일 때만 존재 (Jackson `@JsonInclude(NON_NULL)`) |

**응답 케이스**

| 상황 | HTTP | Body 예시 | 추가 헤더 |
|---|---|---|---|
| 사용 가능 | `200 OK` | `{"available":true,"status":"AVAILABLE"}` | — |
| 이미 가입됨 | `200 OK` | `{"available":false,"status":"ALREADY_REGISTERED"}` | — |
| 형식 오류 / 누락 | `400 Bad Request` | `{"available":false,"status":"INVALID_FORMAT"}` | — |
| 분당 IP 한도 초과 | `429 Too Many Requests` | `{"available":false,"status":"RATE_LIMITED","retryAfterSeconds":42}` | `Retry-After: 42` |

**Rate Limit 정책**

- 키: `rate:email-check:{ip}` (Redis)
- 윈도우: 60초, IP당 최대 10회
- 동작: INCR + 매 호출마다 EXPIRE 갱신 (race 자가 회복 + sliding-ish window)
- Redis 장애 시 fail-open (가용성 우선)
- 클라이언트 IP는 `X-Forwarded-For` 첫 항목 사용 → **nginx 등 신뢰 가능한 리버스 프록시 뒤 배포 가정** (현재 토폴로지: 외부 → nginx(80/443) → backend(8080, 도커 내부망 only))

### 1.2 보안 검토 — Account Enumeration

**위험 표면**: 본 엔드포인트는 입력한 이메일이 가입된 계정인지 명시적으로 노출한다. 따라서 **회원가입 엔드포인트와 동등 수준의 enumeration 위험**을 갖는다 (signup도 중복 이메일에 대해 400을 반환하므로 동일한 정보가 새어나감).

**완화 장치**

| 항목 | 본 API | 회원가입 API | 비고 |
|---|---|---|---|
| Rate limit (per-IP) | 🟩 10/min | ⬜ 미적용 | signup도 동일 정책 적용 권장 (별도 이슈 필요) |
| Account lockout | N/A | N/A | 비밀번호 입력이 아니라 무관 |
| CAPTCHA | ⬜ | ⬜ | SSAFY 범위 외 |

**완화의 한계**

- Rate limit은 enumeration 시도를 **늦출 뿐 막지 못함** — 다중 IP / NAT 우회 / 분산 공격에는 무력
- 진정한 방어는 CAPTCHA 또는 인증 후에만 노출하는 설계지만 UX 트레이드오프 큼
- 본 API의 효용(가입 전 즉시 피드백)이 enumeration 위험보다 큰 것으로 판단해 채택. **운영 트래픽 모니터링 시 동일 IP에서 다량 호출 패턴 감지되면 정책 강화** (한도 축소 / 차단 시간 연장)

**연관 사항**

- signup API도 enumeration 면에서 본 API와 동일 위험. 별도 보안 강화 이슈에서 같이 다루는 것을 권장 (한 API만 rate limit이면 공격자가 다른 쪽으로 우회)
- 토큰 발급(login) 응답 메시지가 이미 "이메일 또는 비밀번호가 올바르지 않습니다"로 통일되어 있어 login 면에서는 enumeration이 차단됨 (사용자 존재 여부 미노출)

### 1.3 비밀번호 변경 API 상세

**요청**

```
PUT /api/auth/password
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**요청 바디** — `PasswordChangeRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|---|---|---|---|---|
| `currentPassword` | string | O | `@NotBlank` | 현재 비밀번호 (BCrypt 검증) |
| `newPassword` | string | O | `@NotBlank` + `@Size(min = 6)` | 새 비밀번호. 6자 이상. 회원가입 정책과 동일 |

**보안 요구사항**

- **JWT Bearer 인증 필수.** `userId`는 `@AuthenticationPrincipal CustomUserPrincipal`에서 추출하며 경로/바디로 받지 않음 (IDOR 방지)
- 소셜 가입 계정(`provider != "email"`, `password IS NULL`)은 변경 불가
- 탈퇴 계정(`deleted_at IS NOT NULL`)은 변경 불가

**응답 케이스**

| 상황 | HTTP | Body | 비고 |
|---|---|---|---|
| 변경 성공 | `204 No Content` | (없음) | 새 비밀번호 BCrypt 인코딩 후 저장 + 모든 RefreshToken 삭제 |
| 새 비밀번호 6자 미만 / 빈 값 | `400 Bad Request` | `{"message":"newPassword: 비밀번호는 6자 이상이어야 합니다"}` | Bean Validation (`@Size`) |
| `currentPassword` 누락 | `400 Bad Request` | `{"message":"currentPassword: 현재 비밀번호는 필수입니다"}` | Bean Validation (`@NotBlank`) |
| 새 비밀번호가 현재와 동일 | `400 Bad Request` | `{"message":"새 비밀번호가 현재 비밀번호와 같습니다"}` | — |
| 탈퇴 / 소셜 계정 | `400 Bad Request` | `{"message":"비밀번호를 변경할 수 없는 계정입니다"}` | — |
| 현재 비밀번호 불일치 | `401 Unauthorized` | `{"message":"현재 비밀번호가 올바르지 않습니다"}` | BCrypt `matches` 실패 |
| Authorization 헤더 누락 / 토큰 만료 | `401 Unauthorized` | (Spring Security 기본) | JWT 필터 단계에서 차단 |

**부수 효과 — RefreshToken 전체 무효화**

변경 성공 시 `refreshTokenService.delete(userId)` 호출 → Redis의 해당 사용자 RefreshToken 삭제.

| 영향 | 동작 |
|---|---|
| 다른 기기 세션 | RefreshToken 무효화 → AccessToken 만료 후 재로그인 필요 |
| 변경을 수행한 본인 세션 | 동일하게 무효화. 현재 AccessToken은 만료 전까지 유효하나 갱신 불가 |
| 기존 RefreshToken으로 `/api/auth/refresh` 호출 시 | "재사용 감지" 경로로 빠져 `400 Bad Request` ("비정상적인 토큰 사용이 감지되었습니다") |

> **정책 근거**: OWASP 권장 사항. 비밀번호 변경은 계정 탈취 의심 또는 보안 강화 목적이므로 모든 활성 세션을 강제 종료. 변경 후 재로그인을 통해 새 RefreshToken을 발급받아야 함.

---

## 2. 사용자 설정 (User)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 내 프로필 조회 | GET | `/api/user/me` | 로그인 사용자 프로필(email, provider) 및 설정값 반환 | ⬜ | 🟠 High |
| 설정 조회 | GET | `/api/user/settings` | 키·체중·당뇨유형·목표혈당·주간시작요일 등 설정값 반환. height/weight `BigDecimal NUMERIC(4,1)`, target_low/high `BigDecimal NUMERIC(5,2)` | 🟩 | 🟠 High |
| 설정 수정 | PUT | `/api/user/settings` | 변경 즉시 대시보드 기준선·알림 임계값 갱신. null 필드는 기존값 유지 (부분 업데이트) | 🟩 | 🟠 High |
| 보호자 목록 조회 | GET | `/api/user/guardians` | `priority` 오름차순 정렬. SOS 연락 순서와 동일 | 🟩 | 🟠 High |
| 보호자 등록 | POST | `/api/user/guardians` | SOS 발송 대상 보호자 등록. `priority`는 서버가 현재 보호자 수로 자동 할당 (Short) | 🟩 | 🟠 High |
| 보호자 수정 | PUT | `/api/user/guardians/{guardianId}` | 관계(`relation`) 수정. null 필드는 기존값 유지 | 🟩 | 🟠 High |
| 보호자 삭제 | DELETE | `/api/user/guardians/{guardianId}` | 삭제 후 나머지 `priority` 자동 재정렬 (빈자리 당기기) | 🟩 | 🟠 High |
| FCM 토큰 등록/갱신 | PUT | `/api/user/fcm-token` | 앱/워치 설치 시 등록. `device_type`: **`ANDROID` / `WATCH`** (대문자 enum). 기존 토큰 있으면 갱신 | 🟩 | 🟠 High |
| FCM 토큰 삭제 | DELETE | `/api/user/fcm-token` | 로그아웃 시 `is_active=false` 처리 | ⬜ | 🟠 High |
| 복용 기록 저장 | POST | `/api/user/medications` | `medications_records` 테이블에 저장. 약/인슐린 구분 없이 `memo` 자유 기술 | ⬜ | 🟡 Medium |
| 복용 기록 조회 | GET | `/api/user/medications?range=7d` | 기간별 복용 기록 조회 | ⬜ | 🟡 Medium |

---

## 3. CGM 연동 (Continuous Glucose Monitoring)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| CGM BLE 연결 및 수신 | (로컬 SDK) | 화이바이오메드 BLE SDK | BLE 5.0. 끊김 시 자동 재연결. 수신 주기마다 UI 갱신 | ⬜ | 🔴 Highest |
| CGM 데이터 서버 동기화 | POST | `/api/cgm/sync` | 앱 → BE. `glucose_records` 테이블에 시계열 INSERT. TLS 1.2+ 암호화. 누락 구간 공백 표시 | ⬜ | 🔴 Highest |
| CGM 패턴 학습 | POST | `/api/cgm/pattern-learn` | 식후 상승/하강 패턴 개인화 학습. 이상치 제거 기준 업데이트 | ⬜ | 🟠 High |
| Samsung Health 연동 | POST | `/api/health/sync` | 운동(`exercise_records`), 수면(`sleep_records`) 수신. 타임라인 핀 표시 | ⬜ | 🟡 Medium |

---

## 4. 대시보드 (Dashboard)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 실시간 혈당 표시 | (로컬) | BLE 로컬 수신 | BLE 수신 주기마다 UI 갱신. 추세 최근 15분 기준 4단계 | ⬜ | 🔴 Highest |
| 타임라인 통합 조회 | GET | `/api/timeline?range={1d\|7d\|30d}` | 혈당 시계열 + 식사/운동/수면 이벤트 핀을 한 번에 반환. range 기본값 `1d`. JWT에서 userId 추출. 4쿼리 `CompletableFuture` 병렬 실행. 응답: `{rangeToken, from, to, glucosePoints[], mealPins[], exercisePins[], sleepPins[]}` | 🟩 | 🔴 Highest |
| 대시보드 요약 지표 | GET | `/api/stats/summary?range=1d` | 실시간 대시보드용 요약(평균, TIR, 최고/최저, 변동폭) | ⬜ | 🔴 Highest |
| 디지털 트윈 아바타 상태 | GET | `/api/avatar/state` | 현재 혈당 기반 아바타 표정·혈관색·애니메이션 매핑. FE 렌더링용 | ⬜ | 🟠 High |
| 혈당 패턴 비교 조회 | GET | `/api/stats/compare?mode={daily\|weekly}&base={date}&compare={date}` | 기준일/비교일의 혈당 곡선·평균·TIR 동시 반환. `delta`로 증감 강조 | ⬜ | 🟡 Medium |

---

## 5. AI 음식 인식 (FastAPI)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 음식 사진 인식 + 공공데이터 자동 연동 | POST | `/ai/food/recognize-and-fetch` | 사진 → CV 모델 인식 → 식품안전처 API 자동 조회 → 영양 정보 반환 (원스텝). **사용 시나리오: 식사 *기록* 흐름** (예측 곡선 불필요) | ⬜ | 🔴 Highest |
| 음식 사진 인식 (CV만) | POST | `/ai/food/recognize` | Camera2 API 촬영 → FastAPI CV 모델. confidence 0.5 미만 시 확인 UI. 실패 시 텍스트 입력 fallback | ⬜ | 🔴 Highest |
| 음식명으로 영양 정보 조회 | GET | `/api/food/search?q={keyword}` | **공공데이터포털 식품영양성분 API(`FoodNtrCpntDbInfo02`)** 호출 후 `foods` 캐싱 (30일 TTL). 캐시 우선 조회. 응답: 영양표시 9대 항목 + 식이섬유 + category(food_lv3_nm) | 🟩 | 🔴 Highest |

> **§7 `/api/predict/glucose/from-image` 와의 책임 구분**: from-image 는 **식전 시뮬레이션** 용 통합 엔드포인트로 CV 인식 + foods 조회 + 예측 모델까지 단일 호출에서 처리. 위 `/ai/food/recognize-and-fetch` 는 **식사 기록** 흐름에서 예측 없이 영양 정보만 필요할 때 사용 (POST `/api/meals` 직전). 두 엔드포인트는 ① CV ② foods 조회 단계의 내부 구현을 공유하지만 호출 시나리오가 다르다.

---

## 6. 커스텀 키보드 MCP

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 키보드 앱 혈당 수치 조회 | GET | `/api/mcp/keyboard/glucose` | 커스텀 키보드가 MCP 통해 현재 혈당 폴링. 키보드 상단 상태바에 표시 | ⬜ | 🟠 High |
| 키보드 음식 키워드 감지 → 위험도 반환 | POST | `/api/mcp/keyboard/food-risk` | 음식명 입력 감지 시 MCP 호출. 현재 혈당 기준 위험도 계산 | ⬜ | 🟠 High |
| 키보드 인라인 배너 알림 | POST | `/api/mcp/keyboard/banner` | `TYPE_APPLICATION_OVERLAY` 오버레이. 현재 혈당 + 음식명 + 위험문구. 3초 자동 소멸 | ⬜ | 🟠 High |
| AccessibilityService 음식 키워드 탐지 | (로컬) | Android AccessibilityService | 허용 앱: 카카오톡·배달의민족. 텍스트 노드 읽기 → 키워드 DB 매칭 → 즉시 폐기 | ⬜ | 🟢 Low |

---

## 7. 프리밀 시뮬레이터 (Pre-meal)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 식후 혈당 곡선 예측 | POST | `/api/predict/glucose` | 음식 영양 데이터 + 유저 프로필(JWT) → AI 예측 모델 호출 → 식후 2시간 혈당 곡선(5분 간격 25포인트) 반환. 현재 `generic` 모드 고정 (personalized 미구현). 결과는 `glucose_predictions` 히스토리에 저장 | 🟩 | 🔴 Highest |
| A/B 비교 모드 | POST | `/api/predict/glucose/compare` | 2개 음식 동시 입력 → 예측 API 병렬 2회 호출 (`CompletableFuture`). 동일 시간축 곡선 비교. 최고 혈당 차이 강조 | 🟩 | 🔴 Highest |
| **사진 통합 식전 예측** | POST | `/api/predict/glucose/from-image` | multipart 이미지 1장 → BE 내부에서 ① CV 인식 ② foods 조회/식약처 API ③ 예측 모델 호출까지 단일 호출 처리. confidence 미달·인식 실패·영양정보 결측은 `Status` enum 으로 분기. 인식 음식이 DB·API 모두 미스면 `customized=true` 로 신규 등록 후 `PENDING_NUTRITION` 응답 | 🟩 | 🔴 Highest |

### 요청 DTO 핵심 타입

`PredictRequest`:
- `foodId: Integer NULL` — `foods.id` 참조 (수동 입력 시 null)
- `foodName: String NotBlank`
- `kcal, carbsG, proteinG, fatG, sugarG: BigDecimal` — `@DecimalMin("0.0")` + 상한 검증
- `giScore: Integer NULL`

### AI 서버 호출 (BE → AI 내부 통신)

| 구간 | Method | 엔드포인트 | 설명 |
|------|--------|-----------|------|
| BE → AI | POST | `{AI_SERVICE_URL}/inference/glucose/meal` | AI 혈당 예측 모델 추론 요청. BE 가 음식 + 유저 데이터를 조합하여 호출. 재시도 3회, 백오프 200/400/800ms, Correlation ID 추적. UserProfile은 외부 DTO라 BigDecimal → `floatValue()`로 변환 |
| BE → AI | POST | `{AI_SERVICE_URL}/api/v1/food/detect` | AI 음식 인식 모델 추론. multipart `file` 파트로 이미지 전송. 재시도/백오프/Correlation ID 정책은 위와 동일 (`FoodDetectClientImpl`). 응답은 `{count, detections[]}` (snake_case JSON), `detections` 는 confidence DESC 정렬됨 |

### `/api/predict/glucose/from-image` 상세

**요청** — `multipart/form-data`, 인증 필수
- `image`: 이미지 파일 (`image/*` content-type, 빈 파일 금지). 위반 시 400.

**응답 — `FromImagePredictResponse`**
```jsonc
{
  "status": "OK | LOW_CONFIDENCE | PENDING_NUTRITION",
  "detected": [                     // CV 가 반환한 모든 항목 (confidence DESC 정렬)
    { "name_ko": "비빔밥", "name_en": "bibimbap", "confidence": 0.91 }
  ],
  "foodId":   1,                    // OK / PENDING_NUTRITION 일 때 채워짐, LOW_CONFIDENCE 는 null
  "foodName": "비빔밥",              // 위와 동일
  "prediction": { /* PredictResponse */ },  // OK 일 때만, 그 외는 null
  "requireConfirmation": false      // LOW_CONFIDENCE 만 true
}
```

**`Status` 분기**

| Status | 트리거 조건 | FE 처리 |
|---|---|---|
| `OK` | 인식 confidence ≥ 0.5 + foods 영양정보 확보 (DB 캐시 hit 또는 식약처 API fetch) | `prediction.curve` 표시 |
| `LOW_CONFIDENCE` | `detections` 비어있음 / top confidence < 0.5 / top `name_ko` blank | `detected[]` 노출, 사용자 선택·텍스트 입력 fallback. `requireConfirmation=true` |
| `PENDING_NUTRITION` | 인식 OK 지만 foods·식약처 API 모두 미스 → `customized=true` row 신규 저장. 영양정보 결측 | "탄수화물 직접 입력" UI. 입력 시 `POST /api/predict/glucose` 재호출, 미입력 시 빈 prediction 그대로 표시 |

> 임계값 `CONFIDENCE_THRESHOLD = 0.5` 은 `FromImagePredictionService` 상수. 변경 시 BE 코드 + 본 스펙 동시 갱신 필요.

**FE 4단계 진행 애니메이션 매핑**

단일 호출이지만 BE 내부 4단계 진행을 FE 가 시각적으로 표현:

| 단계 | 단계명 | 매핑 |
|---|---|---|
| ① | 인식 중 | `FoodDetectClient.detect()` 호출 |
| ② | 영양 정보 조회 중 | `FoodResolutionService.resolve()` (DB → 식약처 API → customized 폴백) |
| ③ | 혈당 예측 중 | `PredictionService.predictForFood()` |
| ④ | 결과 표시 | 응답 수신 |

LOW_CONFIDENCE 는 ① 직후 종료, PENDING_NUTRITION 은 ② 직후 종료 (③ 미실행). FE 는 응답의 `status` 로 어느 단계까지 진행됐는지 역추적 가능.

**호출 시퀀스**

```
FE                     PredictionController         FromImagePredictionService
 │  multipart image     │                            │
 ├──────────────────────►│ 검증(image/* + non-empty)   │
 │                      ├────────────────────────────►│ predict(userId, image)
 │                      │                            │  ├─ FoodDetectClient.detect       (① CV)
 │                      │                            │  │    confidence < 0.5 / blank ko
 │                      │                            │  │    └─ return LOW_CONFIDENCE
 │                      │                            │  ├─ FoodResolutionService.resolve (② foods)
 │                      │                            │  │    PENDING_NUTRITION 분기
 │                      │                            │  │    └─ return PENDING_NUTRITION
 │                      │                            │  └─ PredictionService.predictForFood (③ 예측)
 │                      │                            │       └─ return OK
 │  FromImagePredictResponse                         │
 │◄──────────────────────────────────────────────────│
```

**관련 코드**: [FromImagePredictionService.java](../src/main/java/com/ssafy/s309/domain/prediction/service/FromImagePredictionService.java) · [FromImagePredictResponse.java](../src/main/java/com/ssafy/s309/domain/prediction/dto/FromImagePredictResponse.java) · [PredictionController.java](../src/main/java/com/ssafy/s309/domain/prediction/controller/PredictionController.java)


### 식전 예측 vs 식후 기록 역할 구분

| | 식전 예측 (섹션 7) | 식후 혈당 반응 기록 (섹션 8) |
|---|---|---|
| **시점** | 식사 **전** | 식사 **후** (2시간 경과) |
| **목적** | "이걸 먹으면 혈당이 어떻게 될까?" 시뮬레이션 | 실제 섭취 후 CGM 실측 혈당 반응 저장 |
| **데이터 흐름** | BE → AI 모델 호출 → 예측 곡선 반환 | CGM 실측 데이터 → DB 저장 |
| **DB 테이블** | `glucose_predictions` | `meal_glucose_responses` |
| **AI 의존** | O (모델 추론 필요) | X (실측값 기록) |

> 식후 실측 데이터로 식전 예측의 `accuracy_pct`를 역산하여 모델 정확도를 평가합니다.

---

## 8. 식사 기록 & 성적표 (Meal Logs)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 식사 기록 저장 | POST | `/api/meals` | `meal_records` 테이블에 INSERT. `food_id` NULL 허용(수동 입력). `image_storage_key`로 S3 사진 연결. memo 선택 입력 | ⬜ | 🔴 Highest |
| 식후 혈당 반응 기록 | POST | `/api/meals/{mealId}/response` | 식사 후 2시간 CGM 수집 → `meal_glucose_responses` 테이블에 baseline/peak/slope 저장. 2회 이상 시 `user_food_grades` 등급 산출 | ⬜ | 🟠 High |
| 음식 성적표 조회 | GET | `/api/food-report` | `user_food_grades` 조회. grade 5단계 (S/A/B/C/D), `avg_slope` 기준. 최고혈당/복귀시간은 `meal_glucose_responses.peak_glucose_id`로 join | ⬜ | 🟠 High |

---

## 9. AI 주간 리포트 (Weekly Report)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 주간 리포트 생성 (수동) | POST | `/api/report/weekly` | `weekly_reports`에 INSERT. 매주 월요일 07:00 자동 생성. 수동 버튼 제공. 동기 생성 정책 (status 컬럼 없음, pdf_key NOT NULL). LLM 요약/제안 포함 | ⬜ | 🟡 Medium |
| 주간 리포트 조회 | GET | `/api/report/{reportId}` | 평균혈당·TIR(`time_in_range`)·TAR·TBR·변동폭·LLM요약·개선제안 + GOOD/BAD 음식(`weekly_foods`) | ⬜ | 🟡 Medium |
| PDF 내보내기 | GET | `/api/report/{reportId}/pdf` | A4 1~2장. `pdf_key`로 S3에서 다운로드. 카카오톡/이메일 공유 | ⬜ | 🟡 Medium |

---

## 10. 알림 & 응급 (Alert & Emergency)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 온디바이스 저혈당 감지 | (로컬 ML) | TFLite (로컬) | 온디바이스 감지 → 알림 1초 이내. 무반응 30초 → 스마트홈 자동 대응 | ⬜ | 🔴 Highest |
| 알림 발생 기록 | POST | `/api/alert` | `alerts` 테이블에 INSERT. `alert_type` CHECK 4종(`HIGH/LOW/SOS/WEEKLY_REPORT`). 이후 `smarthome_events` 체인 트리거 | ⬜ | 🔴 Highest |
| SOS 발송 | POST | `/api/alert/sos` | SMS + 앱 푸시 동시 발송. `guardian_notifications` 이력 저장. GPS 위치 + 혈당 + 시각 포함. 미응답 시 `priority` 순서대로 2차 보호자 연속 발송 | ⬜ | 🔴 Highest |
| Galaxy Watch 진동 알림 | (로컬) | Wear OS Notification API | 야간(22:00~07:00) 저혈당 감지 시 진동 강도 상향 | ⬜ | 🟢 Low |

---

## 11. 스마트홈 (Smart Home)

| 기능명 | Method | 엔드포인트 | 상세 설명 | 구현 | 우선순위 |
|--------|--------|-----------|-----------|------|---------|
| 스마트홈 자동 대응 실행 | POST | `/api/smarthome/trigger` | 무반응 30초 후 자동 호출. 조명 빨간 점멸 + 도어락 해제 + SOS 동시 실행 | ⬜ | 🟢 Low |
| 스마트 도어락 자동 해제 | (IoT) | 도어락 IoT API | 무반응 시 자동 잠금 해제. 이벤트 타임스탬프 기록. 보호자 앱 푸시. 원격 재잠금 가능 | ⬜ | 🟢 Low |
| Philips Hue 조명 제어 | (로컬 REST) | Hue Bridge Local REST API | 동일 Wi-Fi 필요. 저혈당 시 빨간색 점멸. 정상 복귀 시 원래 색상 복원. 시연 전용 | ⬜ | 🟢 Low |

---

## 공통 사항

### 인증
- 모든 엔드포인트는 `Authorization: Bearer {accessToken}` 헤더 필요 (섹션 1 제외)
- 서버는 토큰 claims에서 `userId(Integer)` 추출 → SecurityContextHolder에 저장 → Controller에서 `@AuthenticationPrincipal` 로 사용

### 응답 포맷
- **성공:** `200 OK`, `201 Created`, `204 No Content`
- **클라이언트 오류:** `400 Bad Request` (검증 실패, 소유권 위반 등)
- **인증/권한:** `401 Unauthorized`, `403 Forbidden`
- **리소스 없음:** `404 Not Found`
- **외부 서비스 실패:** `503 Service Unavailable` (AI 서버 / 식약처 API 장애 등)

### 오류 응답 형식
```json
{ "message": "존재하지 않는 유저입니다: {userId}" }
```

### 핵심 타입 가이드
- **PK**: 대부분 `Integer` (V1 마이그레이션 기준 INT)
- **시계열 PK**: `Long` 유지 (`glucose_records.id` BIGINT)
- **혈당값/영양소**: `BigDecimal` (`NUMERIC(5,2)`/`(6,2)`/`(8,2)`)
- **신체 정보**: `BigDecimal` (`height/weight NUMERIC(4,1)`, `target_low/high NUMERIC(5,2)`)
- **age, week_start_day, priority**: `Short` (SMALLINT)
- **DeviceType enum**: `ANDROID` / `WATCH` (대문자 저장, `@Enumerated(EnumType.STRING)` + CHECK 제약)

### `diabetesType` 허용값
- `NORMAL` (기본값) / `T1D` / `T2D`

### 보호자 `priority` 동작
- 등록 시: 현재 보호자 수를 `priority`로 자동 할당 (0, 1, 2...)
- 삭제 시: 삭제된 `priority` 이후 항목들이 1씩 앞당겨짐
- 조회 시: `priority ASC` 정렬 → SOS 연락 순서와 일치

### foods 영양 컬럼 (응답 매핑 참고)
공공데이터포털 식품영양성분 API의 응답 필드와 우리 컬럼 매핑:

| 우리 컬럼 | 식약처 필드 |
|---|---|
| `kcal` | `enerc` |
| `carbsG` | `chocdf` |
| `sugarG` | `sugar` |
| `proteinG` | `prot` |
| `fatG` | `fatce` |
| `fiberG` | `fibtg` |
| `saturatedFatG` | `fasat` |
| `transFatG` | `fatrn` |
| `cholesterolMg` | `chole` |
| `sodiumMg` | `nat` |
| `category` | `foodLv3Nm` (식품 대분류) |

### 영양성분 1인분 기준 환산 정책

식약처 API 원본값은 **100g(또는 100ml) 기준** (`nutConSrtrQua` 100 고정)이지만, API 응답(`FoodSearchResult.from()`)에서 `servingSize` 기준으로 환산해 **1인분 기준**으로 반환한다.

**환산 수식**

```
반환값 = DB 저장값(100g 기준) × (servingSize / 100)
```

- `servingSize`가 null이면 100을 기본값으로 사용 → 비율 1.0, 값 변화 없음
- 결과값은 소수점 2자리 반올림 (`RoundingMode.HALF_UP`)

**servingSize 출처**

| 데이터 유형 | servingSize 값 |
|---|---|
| 식약처 API 캐시 | API `SERVING_SIZE` 필드 파싱. 파싱 실패 시 100 기본값 |
| 사용자 커스텀 음식 | 사용자 입력값 |

**환산 예시**

| 음식 | DB 저장값(100g) | servingSize | 반환값(1인분) |
|---|---|---|---|
| 현미밥 | kcal=143, carbs=31.5g | 210g | kcal=300.3, carbs=66.15g |
| 닭가슴살 | kcal=110, protein=23g | 100g | kcal=110, protein=23g (동일) |
| 아메리카노 | kcal=8, carbs=1.5g | 350ml | kcal=28, carbs=5.25g |

**DB 저장 방식은 그대로**

`foods` 테이블의 영양값 컬럼은 계속 **100g 기준**으로 저장. 환산은 응답 레이어(`FoodSearchResult.from()`)에서만 수행하므로 DB 스키마 변경 없음.

**하위 흐름 영향 범위**

| 흐름 | 영향 |
|---|---|
| `POST /api/predict/glucose` (`PredictRequest`) | food search 결과를 그대로 채워 전송하면 1인분 기준 예측 |
| `POST /api/meals` | 식사 기록 시 food search 결과의 영양값이 1인분 기준으로 저장 |
| Agent 음식 검색 (`AgentFoodSearchItem`) | `kcal`, `carbsG`만 포함하므로 동일하게 1인분 기준 |
