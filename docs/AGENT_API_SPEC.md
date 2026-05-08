# AI Agent API 명세서

**Base URL:** `https://{host}/api/agent`  
**인증:** 모든 엔드포인트에 `X-Agent-Api-Key: {api-key}` 헤더 필수  
**Content-Type:** `application/json`

---

## 1. 사용자 프로필 조회

```
GET /api/agent/user-profile
```

당뇨 유형과 목표 혈당 범위를 반환합니다. Agent가 코칭 메시지 생성 전 사용자 기준값을 파악하는 데 사용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |

**Response `200 OK`**

```json
{
  "userId": 3,
  "diabetesType": "T2D",
  "targetGlucoseMin": 70.00,
  "targetGlucoseMax": 180.00
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | Integer | 사용자 ID |
| `diabetesType` | String (enum) | `NORMAL` \| `T1D` \| `T2D` \| `null` (미입력 시) |
| `targetGlucoseMin` | BigDecimal | 목표 혈당 하한 (mg/dL), 미입력 시 `null` |
| `targetGlucoseMax` | BigDecimal | 목표 혈당 상한 (mg/dL), 미입력 시 `null` |

---

## 2. 시계열 혈당 조회

```
GET /api/agent/glucose
```

지정 구간의 혈당 측정값을 시간 오름차순으로 반환합니다. 식후 혈당 추세 분석 및 HIGH/LOW 판단에 활용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |
| `start_time` | LocalDateTime (ISO 8601) | ✅ | 구간 시작 (예: `2026-05-02T00:00:00`) |
| `end_time` | LocalDateTime (ISO 8601) | ✅ | 구간 종료 (예: `2026-05-02T23:59:59`) |

**Response `200 OK`**

```json
[
  { "timestamp": "2026-05-02T08:00:00", "value": 142.0 },
  { "timestamp": "2026-05-02T08:05:00", "value": 156.0 }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | LocalDateTime | 측정 시각 (KST) |
| `value` | BigDecimal | 혈당값 (mg/dL) |

**오류**

| 코드 | 조건 |
|---|---|
| `400` | `start_time > end_time` |

---

## 3. 수면 데이터 조회

```
GET /api/agent/sleep
```

특정 날짜의 수면 시간과 최근 7일 평균 수면 시간을 반환합니다. 기상 후 코칭 맥락 구성에 사용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |
| `date` | LocalDate (YYYY-MM-DD) | ✅ | 기준일 (예: `2026-05-03`) |

**Response `200 OK`**

```json
{
  "date": "2026-05-03",
  "sleepMinutes": 420,
  "averageSleepMinutes": 396.5
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `date` | LocalDate | 기준일 |
| `sleepMinutes` | Integer | 해당 날짜 수면 시간 (분), 데이터 없으면 `0` |
| `averageSleepMinutes` | BigDecimal | `[date-6, date]` 구간 7일 평균 수면 시간 (분), 데이터 없으면 `0` |

---

## 4. 식사 기록 조회

```
GET /api/agent/meals
```

특정 날짜의 식사 목록과 영양 정보를 반환합니다. 식후 코칭 맥락 구성에 사용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |
| `date` | LocalDate (YYYY-MM-DD) | ✅ | 기준일 (예: `2026-05-03`) |

**Response `200 OK`**

```json
[
  {
    "mealId": 12,
    "timestamp": "2026-05-03T12:30:00",
    "foodName": "현미밥",
    "carbs": 45.2,
    "protein": 3.8,
    "fat": 0.6,
    "calories": 200.0
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `mealId` | Integer | 식사 기록 ID |
| `timestamp` | LocalDateTime | 식사 시각 (recorded_at 기준, KST) |
| `foodName` | String | 음식 이름 |
| `carbs` | BigDecimal | 탄수화물 (g) |
| `protein` | BigDecimal | 단백질 (g) |
| `fat` | BigDecimal | 지방 (g) |
| `calories` | BigDecimal | 칼로리 (kcal) |

> 정렬: `recorded_at` ASC. 해당 날짜 기록 없으면 빈 배열 `[]` 반환.

---

## 5. 걸음수 조회

```
GET /api/agent/steps
```

지정 시간 윈도우 내의 구간 걸음수를 반환합니다. 활동량 기반 코칭 맥락 구성에 사용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |
| `start` | LocalDateTime (ISO 8601) | ✅ | 구간 시작 (예: `2026-05-02T13:00:00`) |
| `end` | LocalDateTime (ISO 8601) | ✅ | 구간 종료 (예: `2026-05-02T14:00:00`) |

**Response `200 OK`**

```json
{
  "userId": 3,
  "windowSteps": 1234,
  "from": "2026-05-02T13:00:00",
  "to": "2026-05-02T14:00:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | Integer | 사용자 ID |
| `windowSteps` | Integer | 구간 걸음수 (`MAX(steps_total) - MIN(steps_total)`), 데이터 없으면 `0` |
| `from` | LocalDateTime | 구간 시작 (입력값 echo) |
| `to` | LocalDateTime | 구간 종료 (입력값 echo) |

---

## 6. 알림 이력 조회

```
GET /api/agent/notifications
```

최근 N시간 내 사용자 알림 목록을 반환합니다. 중복 코칭 방지 및 최근 상태 파악에 사용합니다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `user_id` | Integer | ✅ | 조회 대상 사용자 ID |
| `hours` | Integer | ❌ | 최근 N시간 (기본값 `24`, 최대 `720`) |

**Response `200 OK`**

```json
[
  {
    "alertId": 55,
    "alertType": "AGENT_MEAL_FOLLOWUP",
    "message": "식후 혈당이 안정적으로 유지됐어요.",
    "source": "agent",
    "createdAt": "2026-05-03T13:45:00",
    "isRead": false
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `alertId` | Integer | 알림 ID |
| `alertType` | String | 알림 유형 (아래 카탈로그 참고) |
| `message` | String | 알림 본문 |
| `source` | String | `"be"` (룰/스케줄러) \| `"agent"` (Agent 발송) |
| `createdAt` | LocalDateTime | 생성 시각 |
| `isRead` | Boolean | 사용자 읽음 여부 |

---

## 7. 알림 발송

```
POST /api/agent/notifications
```

사용자에게 FCM 푸시 알림을 발송합니다. 30분 내 동일 `alertType` 발송 이력이 있으면 중복 발송을 건너뜁니다.

**Request Body**

```json
{
  "userId": 3,
  "alertType": "AGENT_MEAL_FOLLOWUP",
  "message": "식후 혈당이 목표 범위를 벗어났어요. 다음 식사 때 탄수화물을 줄여보세요."
}
```

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `userId` | Integer | ✅ | - | 수신 사용자 ID |
| `alertType` | String | ✅ | `AGENT_` prefix 필수, 최대 50자 | 알림 유형 |
| `message` | String | ✅ | 최대 500자 | 알림 본문 |

**alertType 카탈로그**

| alertType | FCM 채널 | 설명 |
|---|---|---|
| `AGENT_GLUCOSE_HIGH` | `glucose_critical` | 혈당 고위험 코칭 |
| `AGENT_GLUCOSE_LOW` | `glucose_critical` | 혈당 저위험 코칭 |
| `AGENT_MEAL_FOLLOWUP` | `glucose_coaching` | 식후 코칭 |
| `AGENT_WAKE_UP` | `glucose_coaching` | 기상 후 코칭 |
| `AGENT_SLEEP_INSIGHT` | `glucose_coaching` | 수면 인사이트 |
| `AGENT_GENERIC` | `glucose_coaching` | 기타 코칭 |

**Response**

| 상황 | 상태 코드 | Body |
|---|---|---|
| 발송 성공 | `201 Created` | `{ "alertId": 55, "sent": true, "skipped": false }` |
| 30분 dedup으로 건너뜀 | `200 OK` | `{ "alertId": null, "sent": false, "skipped": true }` |

**오류**

| 코드 | 조건 |
|---|---|
| `400` | `alertType`이 `AGENT_` prefix 없음, 또는 필드 유효성 오류 |

---

## 공통 오류

| 코드 | 설명 |
|---|---|
| `401` | `X-Agent-Api-Key` 헤더 누락 또는 키 불일치 |
| `404` | `user_id`에 해당하는 사용자 없음 |

---

## 미구현 (M2 예정)

| # | 기능 | 설명 |
|---|---|---|
| 8 | `schedule_followup` | Agent 트리거 큐 (`agent_pending_triggers`) — BE → Agent webhook. M2에서 신설 예정 |

---

## 부록 A. message_type / command_type 카탈로그

`chat_messages` 테이블이 알림 도메인을 흡수한 이후의 단일 카테고리 카탈로그.
신규 카테고리는 모두 이 표에 등재한 뒤 코드에 추가한다.

### A.1 `message_type` (sender = `system` 또는 `agent`)

`alert_type`은 외부 호환을 위해 wire format으로만 유지되며, 내부 컬럼/필드는 `message_type`이다.

| sender | message_type | 트리거 | 비고 |
|---|---|---|---|
| `system` | `HIGH` | 혈당 ≥ 180 | 룰 발신, dedup 30분 |
| `system` | `LOW` | 혈당 ≤ 70 | 룰 발신, dedup 30분 |
| `system` | `SOS` | 사용자 SOS 버튼 | guardian 동반 발송 |
| `system` | `WEEKLY_REPORT` | 주간 리포트 스케줄러 | — |
| `agent` | `AGENT_GLUCOSE_HIGH` | Agent 고혈당 코칭 | dedup 30분 |
| `agent` | `AGENT_GLUCOSE_LOW` | Agent 저혈당 코칭 | dedup 30분 |
| `agent` | `AGENT_MEAL_FOLLOWUP` | 식후 60분 활동 권유 | postmeal_agent push |
| `agent` | `AGENT_WAKE_UP` | 기상 후 인사이트 | morning_agent push |
| `agent` | `AGENT_SLEEP_INSIGHT` | 수면 분석 | — |
| `agent` | `AGENT_GENERIC` | 위에 안 잡히는 기타 | — |
| `agent` | `AGENT_RESP_FOOD_RECOMMEND` | 사용자 음식 추천 명령 응답 | parent_id = user command id |
| `agent` | `AGENT_RESP_GLUCOSE_CHECK` | 사용자 혈당 상태 명령 응답 | parent_id = user command id |
| `agent` | `AGENT_RESP_ACTIVITY_TIP` | 사용자 운동 팁 명령 응답 | parent_id = user command id |

**규칙**
- agent 발신은 `AGENT_` prefix 강제 (BE `AgentNotificationService`에서 검증). 룰 type 도용 방지.
- agent 응답(`AGENT_RESP_*`)은 user command 메시지에 대한 응답으로 `parent_id`를 채워서 INSERT한다.

### A.2 `command_type` (sender = `user`)

사용자가 채팅 화면에서 미리 정의된 명령(버튼)을 클릭할 때 INSERT.
`message_type`은 NULL, `command_type`은 NOT NULL.

| command_type | 의미 | payload 예시 |
|---|---|---|
| `USER_REQUEST_FOOD_RECOMMEND` | 음식 추천 요청 | `{ "category": "한식" }` |
| `USER_REQUEST_GLUCOSE_CHECK` | 혈당 상태 요약 요청 | `{}` |
| `USER_REQUEST_ACTIVITY_TIP` | 운동 팁 요청 | `{}` |

**규칙**
- 새 버튼이 추가되면 `USER_REQUEST_*` prefix로 명명하고 이 표에 등재한다.
- payload는 명령 파라미터 (필터/대상 등). 응답 콘텐츠는 agent 메시지의 payload에 들어감.

### A.3 sender별 필드 nullability 매트릭스

| sender | message_type | command_type | parent_id | selected_option_id | options | payload | message |
|---|---|---|---|---|---|---|---|
| `system` | NOT NULL | NULL | NULL | NULL | NULL | nullable | NOT NULL |
| `agent` (push) | NOT NULL | NULL | NULL | NULL | nullable (0~10) | nullable | NOT NULL |
| `agent` (response) | NOT NULL | NULL | NOT NULL | NULL | nullable (0~10) | nullable | NOT NULL |
| `user` (command) | NULL | NOT NULL | NULL | NULL | NULL | nullable | nullable |
| `user` (option reply) | NULL | NULL | NOT NULL | NOT NULL | NULL | NULL | NOT NULL |

V11 `ck_chat_messages_shape` CHECK 제약이 위 매트릭스를 강제한다.
