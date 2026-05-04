# API 명세서

> **상태:** 진행 중 · **담당자:** 도현 · **업데이트:** 2026-05-02
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
| 로그인 | POST | `/api/auth/login` | JWT Bearer 토큰 발급. 탈퇴 사용자(`deleted_at IS NOT NULL`) 차단. 사용자 열거 방지 메시지 통일 | 🟩 | 🔴 Highest |
| 토큰 갱신 | POST | `/api/auth/refresh` | access 만료 시 refresh로 재발급. Refresh Rotation 적용 (재사용 감지 시 모든 토큰 무효화) | 🟩 | 🔴 Highest |
| 로그아웃 | POST | `/api/auth/logout` | Redis의 RefreshToken 삭제 | 🟩 | 🔴 Highest |
| 회원 탈퇴 | DELETE | `/api/auth/withdraw` | 비밀번호 재검증 후 소프트 삭제(`deleted_at` 기록) + 이메일 익명화 + RefreshToken 삭제 | 🟩 | 🟠 High |

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
| 음식 사진 인식 + 공공데이터 자동 연동 | POST | `/ai/food/recognize-and-fetch` | 사진 → CV 모델 인식 → 식품안전처 API 자동 조회 → 영양 정보 반환 (원스텝) | ⬜ | 🔴 Highest |
| 음식 사진 인식 (CV만) | POST | `/ai/food/recognize` | Camera2 API 촬영 → FastAPI CV 모델. confidence 0.6 미만 시 확인 UI. 실패 시 텍스트 입력 fallback | ⬜ | 🔴 Highest |
| 음식명으로 영양 정보 조회 | GET | `/api/food/search?q={keyword}` | **공공데이터포털 식품영양성분 API(`FoodNtrCpntDbInfo02`)** 호출 후 `foods` 캐싱 (30일 TTL). 캐시 우선 조회. 응답: 영양표시 9대 항목 + 식이섬유 + category(food_lv3_nm) | 🟩 | 🔴 Highest |

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

### 요청 DTO 핵심 타입

`PredictRequest`:
- `foodId: Integer NULL` — `foods.id` 참조 (수동 입력 시 null)
- `foodName: String NotBlank`
- `kcal, carbsG, proteinG, fatG, sugarG: BigDecimal` — `@DecimalMin("0.0")` + 상한 검증
- `giScore: Integer NULL`

### AI 서버 호출 (BE → AI 내부 통신)

| 구간 | Method | 엔드포인트 | 설명 |
|------|--------|-----------|------|
| BE → AI | POST | `{AI_SERVICE_URL}/inference/glucose` | AI 모델 추론 요청. BE가 음식 + 유저 데이터를 조합하여 호출. 재시도 3회, 백오프 200/400/800ms, Correlation ID 추적. UserProfile은 외부 DTO라 BigDecimal → `floatValue()`로 변환 |


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

영양성분은 모두 100g(또는 100ml) 기준 (`nutConSrtrQua` 100 고정).
