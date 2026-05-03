# Agent API 검증 시나리오 (M1 / 948 브랜치)

> **대상**: 948 브랜치에서 구현된 6개 Agent API
> 1, 2, 3, 4, 5번 (#971~#987)
>
> **인증**: 모든 Agent API는 `X-Agent-Api-Key: {agent.api-key}` 헤더 필수.
> JWT 인증 X. `AgentApiKeyFilter`가 `/api/agent/**`에서 자동 적용.

---

## 1. 사전 준비

### 1.1 Backend 띄우기

```bash
docker stop s309-backend 2>/dev/null
docker compose -f infra/docker-compose.yml \
                -f infra/docker-compose.override.yml \
                --env-file infra/.env \
                up -d --build backend
```

### 1.2 환경변수 — `infra/.env`

```
AGENT_API_KEY=dev-agent-key-change-in-prod
```

(`application.yml`의 `agent.api-key: ${AGENT_API_KEY:dev-agent-key-change-in-prod}` 기본값과 일치)

### 1.3 시드 사용자

검증 시작 전 사용자 1명 + 혈당/수면/식사/걸음수 데이터 일부 시드 필요.

```bash
# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"agent-test@test.com","password":"pass1234","name":"테스트","phone":"010-0000-0000"}'

# 받은 access_token으로 settings 채우기 (diabetes_type / target_low / target_high)
curl -X PUT http://localhost:8080/api/user/settings \
  -H "Authorization: Bearer {access_token}" \
  -H "Content-Type: application/json" \
  -d '{"diabetesType":"T2D","targetLow":70,"targetHigh":140,"age":35,"gender":"male"}'
```

(혈당/수면/식사 시드는 SQL `INSERT` 또는 별도 추가 시뮬레이터로 채우기)

---

## 2. Agent #1 — GET /api/agent/user-profile

### 2.1 정상 케이스 (200)
```bash
curl "http://localhost:8080/api/agent/user-profile?user_id=1" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```

**예상 응답**:
```json
{
  "userId": 1,
  "diabetesType": "T2D",
  "targetGlucoseMin": 70.00,
  "targetGlucoseMax": 140.00
}
```

### 2.2 프로필 미입력 사용자 (200, null 필드)
프로필 미입력 사용자(diabetes_type/target_low/target_high가 null)는 해당 필드 null로 반환.
```json
{
  "userId": 2,
  "diabetesType": null,
  "targetGlucoseMin": null,
  "targetGlucoseMax": null
}
```

### 2.3 존재하지 않는 사용자 (400)
```bash
curl -i "http://localhost:8080/api/agent/user-profile?user_id=99999" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```
**예상**: HTTP 400 + `{"message":"존재하지 않는 사용자입니다: 99999"}`

### 2.4 인증 누락 (401)
```bash
curl -i "http://localhost:8080/api/agent/user-profile?user_id=1"
```
**예상**: HTTP 401 (AgentApiKeyFilter)

### 2.5 잘못된 키 (401)
```bash
curl -i "http://localhost:8080/api/agent/user-profile?user_id=1" \
  -H "X-Agent-Api-Key: wrong-key"
```
**예상**: HTTP 401

---

## 3. Agent #2 — GET /api/agent/glucose

### 3.1 정상 케이스 (200, 시계열 raw)
```bash
curl "http://localhost:8080/api/agent/glucose?user_id=1&start_time=2026-05-03T00:00:00&end_time=2026-05-03T23:59:59" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```

**예상 응답**:
```json
[
  {"timestamp":"2026-05-03T08:00:00","value":120.5},
  {"timestamp":"2026-05-03T12:30:00","value":165.0},
  {"timestamp":"2026-05-03T18:00:00","value":135.0}
]
```

### 3.2 빈 데이터 (200, 빈 배열)
범위 내 데이터 0건 → `[]` 반환.

### 3.3 start > end (400)
```bash
curl -i "http://localhost:8080/api/agent/glucose?user_id=1&start_time=2026-05-03T23:59:59&end_time=2026-05-03T00:00:00" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```
**예상**: HTTP 400 + `{"message":"start_time은 end_time보다 이전이어야 합니다."}`

### 3.4 인증 누락 (401)
```bash
curl -i "http://localhost:8080/api/agent/glucose?user_id=1&start_time=...&end_time=..."
```
**예상**: HTTP 401

---

## 4. Agent #3 — GET /api/agent/sleep

### 4.1 정상 케이스 (200, 일별 + 7일 평균)
```bash
curl "http://localhost:8080/api/agent/sleep?user_id=1&date=2026-05-03" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```

**예상 응답**:
```json
{
  "date": "2026-05-03",
  "sleepMinutes": 420,
  "averageSleepMinutes": 415.7
}
```

### 4.2 데이터 없음 (200, 0)
해당 일자 daily_health_summaries 행 없음 + 최근 7일도 없음 → `sleepMinutes:0, averageSleepMinutes:0`.

### 4.3 sleep_minutes만 NULL인 행 (200, sleep=0)
```json
{
  "date": "2026-05-03",
  "sleepMinutes": 0,
  "averageSleepMinutes": 380.0
}
```

### 4.4 7일 모두 NULL (200, average=0)
AVG가 NULL 반환 → Service에서 0 처리.

### 4.5 인증 누락 (401)

---

## 5. Agent #4 — GET /api/agent/meals

### 5.1 정상 케이스 (200, 일별 식사 + 영양)
```bash
curl "http://localhost:8080/api/agent/meals?user_id=1&date=2026-05-03" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```

**예상 응답** (recorded_at ASC):
```json
[
  {
    "mealId": 1,
    "timestamp": "2026-05-03T08:00:00",
    "foodName": "현미밥",
    "carbs": 35.20,
    "protein": 4.50,
    "fat": 0.80,
    "calories": 165.00
  },
  {
    "mealId": 2,
    "timestamp": "2026-05-03T12:30:00",
    "foodName": "김치찌개",
    "carbs": 18.00,
    "protein": 12.00,
    "fat": 6.50,
    "calories": 180.00
  }
]
```

### 5.2 빈 데이터 (200, 빈 배열)

### 5.3 food_id가 매칭 안 되는 meal (200, 자동 제외)
theta join (`m.foodId = f.id`) 특성상 매칭 안 되는 meal은 응답에서 빠짐.

### 5.4 인증 누락 (401)

---

## 6. Agent #5 — GET /api/agent/steps (이미 구현)

### 6.1 정상 케이스 (200)
```bash
curl "http://localhost:8080/api/agent/steps?user_id=1&start=2026-05-03T13:00:00&end=2026-05-03T14:00:00" \
  -H "X-Agent-Api-Key: dev-agent-key-change-in-prod"
```

**예상 응답**:
```json
{
  "userId": 1,
  "windowSteps": 1234,
  "from": "2026-05-03T13:00:00",
  "to": "2026-05-03T14:00:00"
}
```

### 6.2 데이터 없음 (200, windowSteps=0)

### 6.3 start > end (400)

### 6.4 인증 누락 (401)

---

## 7. 통합 검증 체크리스트

| API | 정상 200 | 빈 데이터 200 | 잘못된 파라미터 400 | 인증 누락 401 |
|---|---|---|---|---|
| #1 user-profile | ☐ | N/A | ☐ (없는 user_id) | ☐ |
| #2 glucose | ☐ | ☐ | ☐ (start>end) | ☐ |
| #3 sleep | ☐ | ☐ | N/A | ☐ |
| #4 meals | ☐ | ☐ | N/A | ☐ |
| #5 steps | ☐ | ☐ | ☐ (start>end) | ☐ |

---

## 8. Swagger UI

`http://localhost:8080/swagger-ui.html` — 각 Agent API 그룹(`AgentUserProfile`, `AgentGlucose`, `AgentSleep`, `AgentMeals`, `AgentSteps`)에서 시그니처 + 예제 확인 가능.

다만 Swagger UI는 JWT Bearer 인증만 지원하므로 Agent API의 `X-Agent-Api-Key` 헤더는 직접 추가가 어려움 — **curl로 검증 권장**.

---

## 9. DB 직접 시드 (선택, SQL)

검증 사용자 1명 시드 후 헬스/식사 데이터 채우기:

```sql
-- 유저 (signup으로 생성된 user_id 기준)
UPDATE users SET diabetes_type='T2D', target_low=70, target_high=140 WHERE id=1;

-- daily_health_summaries (수면 + 메트릭)
INSERT INTO daily_health_summaries (user_id, date, steps, calories_burned, sleep_minutes, avg_heart_rate)
VALUES (1, '2026-05-03', 8400, 320.5, 420, 75.3);

-- 7일치 수면
INSERT INTO daily_health_summaries (user_id, date, sleep_minutes)
VALUES
  (1, '2026-04-27', 410), (1, '2026-04-28', 425), (1, '2026-04-29', 430),
  (1, '2026-04-30', 400), (1, '2026-05-01', 415), (1, '2026-05-02', 420);

-- 혈당 (glucose_records)
INSERT INTO glucose_records (user_id, value, measured_at)
VALUES
  (1, 120.5, '2026-05-03T08:00:00'),
  (1, 165.0, '2026-05-03T12:30:00'),
  (1, 135.0, '2026-05-03T18:00:00');

-- 식사 (foods + meal_records)
INSERT INTO foods (name, kcal, carbs_g, sugar_g, protein_g, fat_g, is_customized, search_count, cached_at)
VALUES ('현미밥', 165.00, 35.20, 1.50, 4.50, 0.80, false, 0, NOW())
RETURNING id;  -- e.g. food_id = 1

INSERT INTO meal_records (user_id, food_id, is_processed, recorded_at)
VALUES
  (1, 1, false, '2026-05-03T08:00:00'),
  (1, 1, false, '2026-05-03T12:30:00');

-- 걸음수 시계열
INSERT INTO health_snapshots (user_id, recorded_at, steps_total, calories_burned, heart_rate)
VALUES
  (1, '2026-05-03T13:00:00', 5000, 200.0, 78),
  (1, '2026-05-03T13:30:00', 5500, 220.0, 82),
  (1, '2026-05-03T14:00:00', 6234, 245.0, 75);
```
