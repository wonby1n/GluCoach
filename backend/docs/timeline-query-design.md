# 타임라인 통합 조회 쿼리·인덱스 설계

> **작성일**: 2026-04-30
> **이슈**: [S14P31S309-293]
> **상위 스토리**: [CGM-BE] 타임라인 통합 조회 API (`GET /api/timeline?range={range}`)
> **상태**: 설계 + 측정 테스트 작성 (실측 결과는 IDE 실행 후 § 9-6에 기입)

---

## 1. 목적

`GET /api/timeline` 엔드포인트가 한 번의 호출로 반환해야 할 4종 데이터(혈당 시계열 + 식사/운동/수면 이벤트)에 대한 **DB 쿼리 전략과 인덱스 설계**를 정의한다. 7일 범위 조회 응답 300ms 이내 달성을 목표로 한다.

---

## 2. 대상 테이블 및 ERD 인덱스

ERD 기준으로 4개 테이블 모두 `(user_id, time_column)` 복합 인덱스가 정의되어 있다 — **추가 인덱스 불필요**.

| 테이블 | 시간 컬럼 | 기존 인덱스 | 비고 |
|--------|-----------|------------|------|
| `glucose_records` | `measured_at` | `idx_glucose_user_time (user_id, measured_at)` | 점-시간 (CGM 측정값) |
| `meal_records` | `recorded_at` | `idx_meal_user_time (user_id, recorded_at)` | 점-시간 (식사 시각) |
| `exercise_records` | `started_at`, `ended_at` | `idx_ex_user_time (user_id, started_at)` | 구간-시간 (운동 세션) |
| `sleep_records` | `started_at`, `ended_at` | `idx_sleep_user_time (user_id, started_at)` | 구간-시간 (수면 세션) |

> 운동·수면은 `started_at`만 인덱스에 포함. 구간 겹침 쿼리에서 `ended_at` 필터는 인덱스 후보 row에 대해서만 적용되므로 추가 인덱스 없이도 충분히 빠르다 (이벤트 수가 적음).

---

## 3. 쿼리 전략 — 테이블별 병렬 4쿼리

### 결정: 단일 통합 쿼리(UNION/JOIN) 대신 **4개 독립 쿼리 병렬 실행**

이유:

1. **스키마 이질성**: 4개 테이블의 컬럼이 모두 다름. UNION ALL은 더미 컬럼으로 어색해지고, JOIN은 카티시안 곱으로 의미 없음.
2. **인덱스 사용 효율**: 각 쿼리가 자기 테이블의 `(user_id, time)` 복합 인덱스를 그대로 사용 — index range scan으로 가장 빠른 경로.
3. **병렬화 용이**: `CompletableFuture.supplyAsync` 4개로 병렬화 가능. 응답 시간 = `max(개별 쿼리 시간)`.
4. **유지보수성**: 테이블별 매핑/프로젝션이 명확. 한 테이블 스키마 변경이 다른 쿼리에 전파되지 않음.

### 검토하고 기각한 대안

| 대안 | 기각 이유 |
|------|-----------|
| UNION ALL + type 컬럼 | 4종의 다른 컬럼을 동일 row에 욱여넣어야 함 → 더미 NULL 컬럼 다수, 가독성 저하 |
| 통합 timeline 테이블 (denormalize) | 4개 테이블 INSERT마다 trigger/이벤트 발행 필요. CGM은 5분마다 적재 → 쓰기 증폭 큼. 7일 데이터량 작아 read-side 최적화 가치 ↓ |
| 캐싱 (Redis 등) | 데이터 freshness 중요(실시간 혈당). 캐시 무효화 비용이 read 시간보다 클 수 있음. 우선 DB만으로 목표 달성 시도 |

---

## 4. 쿼리 SQL 정의

### 4-1. 혈당 시계열 (`glucose_records`)

```sql
-- noinspection SqlResolveForFile
SELECT id, value, measured_at
FROM glucose_records
WHERE user_id = ?
  AND measured_at BETWEEN ? AND ?
ORDER BY measured_at;
```

- 인덱스: `idx_glucose_user_time (user_id, measured_at)` — leftmost 매칭, range scan
- 정렬: 인덱스 순서 그대로 → file sort 없음

### 4-2. 식사 핀 (`meal_records`)

```sql
-- noinspection SqlResolveForFile
SELECT id, food_id, recorded_at, image_storage_key
FROM meal_records
WHERE user_id = ?
  AND recorded_at BETWEEN ? AND ?
ORDER BY recorded_at;
```

- 인덱스: `idx_meal_user_time (user_id, recorded_at)`
- `food_id`는 nullable (수동 입력). 음식명 표시용 데이터는 별도 join 또는 N+1 회피 위해 lazy load 권장 (이번 설계 범위 외)

### 4-3. 운동 세션 (`exercise_records`) — 구간 겹침

```sql
-- noinspection SqlResolveForFile
SELECT id, exercise_type, calories, started_at, ended_at
FROM exercise_records
WHERE user_id = ?
  AND started_at <= ?  -- to
  AND ended_at   >= ?  -- from
ORDER BY started_at;
```

겹침 조건의 표준 형태: 두 구간 `[A.start, A.end]`, `[B.start, B.end]`이 겹치려면 `A.start <= B.end AND A.end >= B.start`.

- 인덱스: `idx_ex_user_time (user_id, started_at)` — `started_at <= :to` 부분이 인덱스 사용
- `ended_at >= :from`은 인덱스 후보에 대한 후속 필터 (PostgreSQL `Filter:` 노드)
- 운동 이벤트가 적어(7일에 7~14건) 후속 필터 비용 무시 가능

### 4-4. 수면 세션 (`sleep_records`) — 구간 겹침

```sql
-- noinspection SqlResolveForFile
SELECT id, started_at, ended_at
FROM sleep_records
WHERE user_id = ?
  AND started_at <= ?  -- to
  AND ended_at   >= ?  -- from
ORDER BY started_at;
```

운동과 동일한 구조. 인덱스 `idx_sleep_user_time (user_id, started_at)` 활용.

---

## 5. Repository 메서드 시그니처 (Spring Data JPA)

엔티티 클래스가 생성된 후 적용할 시그니처. 메서드 명명 규칙은 Spring Data 파생 쿼리 + 필요 시 `@Query`.

```java
// GlucoseRecordRepository
List<GlucoseRecord> findByUser_IdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
    Long userId, LocalDateTime from, LocalDateTime to);

// MealRecordRepository
List<MealRecord> findByUser_IdAndRecordedAtBetweenOrderByRecordedAtAsc(
    Long userId, LocalDateTime from, LocalDateTime to);

// ExerciseRecordRepository
@Query("""
    SELECT e FROM ExerciseRecord e
     WHERE e.user.id = :userId
       AND e.startedAt <= :to
       AND e.endedAt   >= :from
     ORDER BY e.startedAt ASC
    """)
List<ExerciseRecord> findOverlappingByUserId(
    @Param("userId") Long userId,
    @Param("from") LocalDateTime from,
    @Param("to")   LocalDateTime to);

// SleepRecordRepository — exercise와 동일한 구조
@Query("""
    SELECT s FROM SleepRecord s
     WHERE s.user.id = :userId
       AND s.startedAt <= :to
       AND s.endedAt   >= :from
     ORDER BY s.startedAt ASC
    """)
List<SleepRecord> findOverlappingByUserId(
    @Param("userId") Long userId,
    @Param("from") LocalDateTime from,
    @Param("to")   LocalDateTime to);
```

> 파생 쿼리(메서드명) vs `@Query` 선택 기준: 단순 BETWEEN은 파생, 두 컬럼 동시 비교(겹침)는 가독성을 위해 `@Query` 사용.

---

## 6. 서비스 계층 — 4쿼리 병렬 합성

```java
@Service
@RequiredArgsConstructor
public class TimelineService {

  private final GlucoseRecordRepository glucoseRepo;
  private final MealRecordRepository mealRepo;
  private final ExerciseRecordRepository exerciseRepo;
  private final SleepRecordRepository sleepRepo;

  public TimelineResponse getTimeline(Long userId, LocalDateTime from, LocalDateTime to) {
    var glucose  = supplyAsync(() -> glucoseRepo.findByUser_IdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, from, to));
    var meals    = supplyAsync(() -> mealRepo.findByUser_IdAndRecordedAtBetweenOrderByRecordedAtAsc(userId, from, to));
    var exercise = supplyAsync(() -> exerciseRepo.findOverlappingByUserId(userId, from, to));
    var sleep    = supplyAsync(() -> sleepRepo.findOverlappingByUserId(userId, from, to));

    return new TimelineResponse(from, to,
        glucose.join(), meals.join(), exercise.join(), sleep.join());
  }
}
```

> `@Async` 또는 `CompletableFuture.supplyAsync` 어느 쪽이든 가능. 단순함을 위해 후자 권장. 트랜잭션 경계가 다른 스레드를 넘어가지 않도록 각 repository 호출은 자기 트랜잭션 (read-only) 내에서 완결.

---

## 7. 인덱스 활용 분석 (EXPLAIN 예상)

PostgreSQL 16 기준 예상 출력. 7일 조회, user_id = 1 가정.

### 7-1. 혈당 (5분 간격 2,016행)

```
Index Scan using idx_glucose_user_time on glucose_records
  Index Cond: ((user_id = 1) AND (measured_at >= '2026-04-23') AND (measured_at <= '2026-04-30'))
```

- 복합 인덱스 `(user_id, measured_at)` 양쪽 컬럼 모두 Index Cond에 포함 → 순수 인덱스 범위 스캔
- 정렬은 인덱스 순서 그대로 → Sort 노드 없음

### 7-2. 식사 (~30행)

```
Index Scan using idx_meal_user_time on meal_records
  Index Cond: ((user_id = 1) AND (recorded_at >= '...') AND (recorded_at <= '...'))
```

### 7-3. 운동/수면 (구간 겹침, ~10행)

```
Index Scan using idx_ex_user_time on exercise_records
  Index Cond: ((user_id = 1) AND (started_at <= '...'))
  Filter:    (ended_at >= '...')
```

- `(user_id, started_at)` 인덱스로 후보 row 추출 후, `ended_at >= :from`은 Filter 단계에서 적용
- 7일 윈도우에서 후보 row가 ~10건 이내이므로 Filter 비용 무시 가능
- 수면도 동일 구조 (`idx_sleep_user_time`)

---

## 8. 성능 추정

### 데이터 볼륨 (7일, 1유저)

| 종류 | 간격 | 7일 행수 |
|------|------|---------|
| 혈당 (5분) | 5분 | 2,016 |
| 혈당 (15분) | 15분 | 672 |
| 식사 | 3~5/일 | 21~35 |
| 운동 | 1~2/일 | 7~14 |
| 수면 | 1/일 | 7 |
| **합계 (5분 기준)** | | **~2,070** |

### 응답 시간 추정

| 구간 | 추정 시간 |
|------|----------|
| 4쿼리 max (warm cache, index range scan) | 10~30ms |
| JPA → entity 매핑 (~2,070 row) | 20~50ms |
| 직렬화 (JSON ~2,070 point) | 10~30ms |
| 네트워크/Spring 오버헤드 | 20~50ms |
| **총합** | **60~160ms** |

→ **목표 300ms 대비 충분한 여유**. cold cache 시에도 200ms 내외 예상.

### 우려 시나리오

| 시나리오 | 영향 | 대응 |
|----------|------|------|
| 5분 간격 30일 조회 (8,640행) | JSON 직렬화 시간 ↑ | API 측에서 최대 range 제한 (제안: 31일) |
| user_id 카디널리티 매우 낮음 | optimizer가 index 무시 가능성 | `FORCE INDEX` 또는 인덱스 통계 갱신 |
| 동일 시점 다중 호출 | DB connection 고갈 | 4쿼리 병렬이지만 각 호출당 4 connection — pool size 모니터링 |

---

## 9. 성능 측정

### 9-1. 측정 환경

- DB: PostgreSQL 16 (운영 동일)
- 인프라: Testcontainers (Docker)
- 측정 도구: `JdbcTemplate` 직접 실행 + `System.nanoTime()` (JPA 오버헤드 제외, 순수 쿼리/인덱스 성능)
- 테스트 파일: `src/test/java/com/ssafy/s309/timeline/TimelineQueryPerformanceTest.java`

### 9-2. 시드 데이터

| 종류 | 대상 유저 | 노이즈 유저 | 합계 |
|------|----------|-----------|------|
| 혈당 (5분 간격, 7일) | 2,016 | 5명 × 2,016 = 10,080 | 12,096 |
| 식사 (4/일 × 7일) | 28 | 5 × 28 = 140 | 168 |
| 운동 (1/일 × 7일) | 7 | 5 × 7 = 35 | 42 |
| 수면 (1/일 × 7일) | 7 | 5 × 7 = 35 | 42 |

노이즈 유저는 인덱스의 `user_id` 선택성 효과를 검증하기 위함.

### 9-3. 측정 시나리오

1. **Warmup 3회**: JIT/캐시 워밍
2. **측정 5회**: 4쿼리 직렬 실행 시간 평균
3. **EXPLAIN ANALYZE**: 4 테이블 모두 `idx_*_user_time` 인덱스 사용 검증

### 9-4. 합격 기준

- 4쿼리 직렬 합계 평균 **< 300ms** (병렬 시 더 빠름)
- 4쿼리 모두 EXPLAIN에서 `idx_*_user_time` 사용 확인

### 9-5. 실행 방법 (Docker Desktop 필요)

**IDE (IntelliJ)**:
- `TimelineQueryPerformanceTest.java` 우클릭 → Run

**CLI (PowerShell / Git Bash)**:
```bash
cd C:\Users\SSAFY\Desktop\dohyun\S14P31S309\backend
.\gradlew.bat test --tests "com.ssafy.s309.timeline.TimelineQueryPerformanceTest" -i
```

### 9-6. 측정 결과

> 본 섹션은 실제 실행 후 채워질 예정.

```
=== 타임라인 쿼리 성능 측정 (PostgreSQL 16) ===
범위        : 2026-04-23T00:00 ~ 2026-04-30T00:00 (7일)
대상 유저   : 1 (혈당 2,016 / 식사 28 / 운동 7 / 수면 7)
노이즈 유저 : 5명
warmup      : 3회
측정        : 5회 평균
개별 평균   : glucose=??ms / meal=??ms / exercise=??ms / sleep=??ms
4쿼리 합계 평균  : ??ms
개별 쿼리 최대   : ??ms
==========================================
```

---

## 10. 의존성 및 후속 작업

| 항목 | 담당 스토리 | 비고 |
|------|------------|------|
| `GlucoseRecord` entity + repository | CGM-BE (적재 스토리, `POST /api/cgm/sync`) | |
| `MealRecord` entity + repository | MEAL-BE (식사 기록 스토리) | 상위 스토리에 이미 정의 |
| `ExerciseRecord` entity + repository | HEALTH-SYNC (Samsung Health 연동) | |
| `SleepRecord` entity + repository | HEALTH-SYNC | |
| 시드 데이터 + 성능 측정 | 본 스토리 잔여 또는 별도 측정 스토리 | entity 모두 생성 후 |

---

## 11. 결론

- **인덱스 추가 불필요**: ERD에 정의된 `(user_id, time)` 복합 인덱스 4개로 충분
- **쿼리 전략**: 4개 독립 쿼리를 `CompletableFuture`로 병렬 실행
- **구간 이벤트(운동/수면)**: 표준 겹침 조건 (`start <= :to AND end >= :from`)
- **응답 시간 추정**: 60~160ms (warm) / 200ms 내외 (cold) — 목표 300ms 내 여유
- **실측은 entity 생성 후**: 후속 스토리에서 시드 + 측정 진행
