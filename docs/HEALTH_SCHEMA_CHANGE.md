# 헬스 저장 구조 개편 (V2)

## 배경

기존 `sleep_records`, `exercise_records` 테이블은 **세션 기반(시작/종료 시각)** 으로 설계됐지만 실제로 다음 문제가 있었음:

1. 백엔드에 Service/Controller 미구현 — 데이터가 들어온 적 없음
2. 프론트는 삼성 헬스에서 **하루 누적값**(걸음수, 칼로리)을 받고 있어 세션 모델과 맞지 않음
3. AI Agent가 필요로 하는 건 일별 합계 / 평균이라 세션 단위 저장은 과함

## 변경

| | Before | After |
|---|---|---|
| 테이블 | `sleep_records`, `exercise_records` | `daily_health_summaries` 1개 |
| 키 | `id` (세션별) | `(user_id, date)` upsert |
| 저장 단위 | 세션 시작~종료 | 하루 1행 |

## 새 테이블

```sql
daily_health_summaries (
    user_id, date,
    steps,
    calories_burned,
    sleep_minutes,
    avg_heart_rate,
    updated_at
)
PRIMARY KEY (user_id, date)
```

수면은 분 단위만 보관. 시작/종료 시각은 `getLastSleepDurationMinutes()`가 SDK 안에서 이미 분으로 변환하므로 프론트가 보낼 일이 없고, Agent도 분만 요구함. 향후 타임라인 핀이 시각을 요구하면 별도 마이그레이션으로 추가.

## 헬스 데이터 저장 흐름

```
프론트 (MainActivity.startSamsungHealthPolling, 60초 간격)
    ↓
삼성 헬스 SDK
  - getTodaySteps()
  - getTodayActiveCalories()
  - getLastSleepDurationMinutes()
  - getLatestHeartRate()
    ↓
POST /api/health/daily-summary  (오늘 날짜로 upsert)
    ↓
daily_health_summaries
```

- 1분마다 누적값을 보내고 서버는 (user_id, today)로 덮어쓰기 → 항상 최신 상태 1행
- 수면은 wake-up date를 키로 함 (산업 표준)
- 앱 재시작 시 GET으로 서버 값을 불러와 표시 (현재 Mock fallback 대체)

## 영향받는 코드 (후속 작업)

- ❌ 삭제: `ExerciseRecord`, `SleepRecord`, 두 Repository
- ⚠️ 수정: `TimelineService`
  - `ExerciseRecordRepository`, `SleepRecordRepository` 의존성 제거
  - `exercises`, `sleeps` 빈 리스트 반환 (DTO/필드는 유지 — 추후 구현)
- ➕ 추가: `DailyHealthSummary` Entity / Repository / Service / Controller
- ✅ 유지: `ExercisePin`, `SleepPin`, `TimelineResponse` 구조 (프론트 미구현, 추후 사용 예정)

## API 명세 (후속 작업에서 구현)

```
POST /api/health/daily-summary
  body: { date, steps, caloriesBurned, sleepMinutes, avgHeartRate }
  → upsert by (user_id, date)

GET /api/health/daily-summary?from=YYYY-MM-DD&to=YYYY-MM-DD
  → 기간 내 일별 요약 리스트
```
