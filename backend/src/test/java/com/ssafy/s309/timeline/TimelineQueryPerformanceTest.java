package com.ssafy.s309.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 타임라인 통합 조회 쿼리·인덱스 설계 [#293] 성능 검증.
 *
 * <p>Testcontainers로 PostgreSQL 16을 띄워 ERD 인덱스 정의를 적용한 뒤, 7일 범위 4쿼리(혈당/식사/운동/수면)의 합계 응답 시간이 300ms
 * 이내인지 측정한다. 실행에는 Docker가 필요하다 (WSL 환경에서는 IDE/Windows에서 실행).
 */
@Testcontainers
@SuppressWarnings("NonAsciiCharacters")
class TimelineQueryPerformanceTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final long TARGET_USER_ID = 1L;
  private static final int NOISE_USER_COUNT = 5;
  private static final LocalDateTime TO = LocalDateTime.of(2026, 4, 30, 0, 0);
  private static final LocalDateTime FROM = TO.minusDays(7);
  private static final int GLUCOSE_INTERVAL_MIN = 5;

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void setUp() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName(POSTGRES.getDriverClassName());
    ds.setUrl(POSTGRES.getJdbcUrl());
    ds.setUsername(POSTGRES.getUsername());
    ds.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(ds);

    applySchema();
    seedData();
    jdbc.execute("ANALYZE");
  }

  // ── Schema (ERD 기준 PostgreSQL 변환) ─────────────────────────

  private static void applySchema() {
    jdbc.execute(
        """
        CREATE TABLE glucose_records (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL,
          value NUMERIC(5,2) NOT NULL,
          measured_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbc.execute("CREATE INDEX idx_glucose_user_time ON glucose_records (user_id, measured_at)");

    jdbc.execute(
        """
        CREATE TABLE meal_records (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL,
          food_id BIGINT,
          food_name VARCHAR(100),
          recorded_at TIMESTAMP NOT NULL,
          image_storage_key VARCHAR(255),
          is_processed BOOLEAN NOT NULL DEFAULT FALSE,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbc.execute("CREATE INDEX idx_meal_user_time ON meal_records (user_id, recorded_at)");

    jdbc.execute(
        """
        CREATE TABLE exercise_records (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL,
          exercise_type VARCHAR(20) NOT NULL,
          calories NUMERIC(6,2),
          started_at TIMESTAMP NOT NULL,
          ended_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbc.execute("CREATE INDEX idx_ex_user_time ON exercise_records (user_id, started_at)");

    jdbc.execute(
        """
        CREATE TABLE sleep_records (
          id BIGSERIAL PRIMARY KEY,
          user_id BIGINT NOT NULL,
          started_at TIMESTAMP NOT NULL,
          ended_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """);
    jdbc.execute("CREATE INDEX idx_sleep_user_time ON sleep_records (user_id, started_at)");
  }

  // ── Seed (target user 1명 + noise NOISE_USER_COUNT명) ─────────

  private static void seedData() {
    int totalUsers = NOISE_USER_COUNT + 1;
    for (long uid = 1; uid <= totalUsers; uid++) {
      seedGlucose(uid);
      seedMeals(uid);
      seedExercise(uid);
      seedSleep(uid);
    }
  }

  private static void seedGlucose(long userId) {
    int totalPoints = 7 * 24 * 60 / GLUCOSE_INTERVAL_MIN;
    List<Object[]> batch = new ArrayList<>(totalPoints);
    LocalDateTime t = FROM;
    for (int i = 0; i < totalPoints; i++) {
      batch.add(new Object[] {userId, 100.0 + (i % 50), Timestamp.valueOf(t)});
      t = t.plusMinutes(GLUCOSE_INTERVAL_MIN);
    }
    jdbc.batchUpdate(
        "INSERT INTO glucose_records (user_id, value, measured_at) VALUES (?, ?, ?)", batch);
  }

  private static void seedMeals(long userId) {
    List<Object[]> batch = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      for (int meal = 0; meal < 4; meal++) {
        LocalDateTime t = FROM.plusDays(day).plusHours(8L + meal * 4L);
        batch.add(new Object[] {userId, "음식" + meal, Timestamp.valueOf(t)});
      }
    }
    jdbc.batchUpdate(
        "INSERT INTO meal_records (user_id, food_name, recorded_at) VALUES (?, ?, ?)", batch);
  }

  private static void seedExercise(long userId) {
    List<Object[]> batch = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      LocalDateTime start = FROM.plusDays(day).plusHours(18);
      batch.add(
          new Object[] {
            userId, "WALKING", Timestamp.valueOf(start), Timestamp.valueOf(start.plusMinutes(30))
          });
    }
    jdbc.batchUpdate(
        "INSERT INTO exercise_records (user_id, exercise_type, started_at, ended_at) "
            + "VALUES (?, ?, ?, ?)",
        batch);
  }

  private static void seedSleep(long userId) {
    List<Object[]> batch = new ArrayList<>();
    for (int day = 0; day < 7; day++) {
      LocalDateTime start = FROM.plusDays(day).plusHours(23);
      batch.add(
          new Object[] {userId, Timestamp.valueOf(start), Timestamp.valueOf(start.plusHours(7))});
    }
    jdbc.batchUpdate(
        "INSERT INTO sleep_records (user_id, started_at, ended_at) VALUES (?, ?, ?)", batch);
  }

  // ── 성능 측정 ────────────────────────────────────────────────

  @Test
  @DisplayName("4쿼리 직렬 합계 < 300ms (warmup 3회 + 측정 5회 평균)")
  void 타임라인_4쿼리_7일_300ms_이내() {
    for (int i = 0; i < 3; i++) {
      runAllQueries();
    }

    int iterations = 5;
    long totalSum = 0;
    long globalMax = 0;
    long[] perQuerySum = new long[4];

    for (int i = 0; i < iterations; i++) {
      long[] times = runAllQueries();
      long sum = times[0] + times[1] + times[2] + times[3];
      long iterationMax = Math.max(Math.max(times[0], times[1]), Math.max(times[2], times[3]));
      totalSum += sum;
      globalMax = Math.max(globalMax, iterationMax);
      for (int j = 0; j < 4; j++) {
        perQuerySum[j] += times[j];
      }
    }

    long avgTotal = totalSum / iterations;

    System.out.println();
    System.out.println("=== 타임라인 쿼리 성능 측정 (PostgreSQL 16) ===");
    System.out.printf("범위        : %s ~ %s (7일)%n", FROM, TO);
    System.out.printf("대상 유저   : %d (혈당 2,016 / 식사 28 / 운동 7 / 수면 7)%n", TARGET_USER_ID);
    System.out.printf("노이즈 유저 : %d명%n", NOISE_USER_COUNT);
    System.out.printf("warmup      : 3회%n");
    System.out.printf("측정        : %d회 평균%n", iterations);
    System.out.printf(
        "개별 평균   : glucose=%dms / meal=%dms / exercise=%dms / sleep=%dms%n",
        perQuerySum[0] / iterations,
        perQuerySum[1] / iterations,
        perQuerySum[2] / iterations,
        perQuerySum[3] / iterations);
    System.out.printf("4쿼리 합계 평균  : %dms%n", avgTotal);
    System.out.printf("개별 쿼리 최대   : %dms%n", globalMax);
    System.out.println("==========================================");
    System.out.println();

    assertThat(avgTotal).as("4쿼리 직렬 합계 평균 < 300ms").isLessThan(300L);
  }

  @Test
  @DisplayName("EXPLAIN ANALYZE로 인덱스 사용 검증 (4 테이블 모두 idx_*_user_time 사용)")
  void 인덱스_사용_검증() {
    String glucosePlan =
        explain(
            "SELECT id, value, measured_at FROM glucose_records "
                + "WHERE user_id = ? AND measured_at BETWEEN ? AND ? ORDER BY measured_at",
            TARGET_USER_ID,
            Timestamp.valueOf(FROM),
            Timestamp.valueOf(TO));

    String mealPlan =
        explain(
            "SELECT id, food_id, recorded_at FROM meal_records "
                + "WHERE user_id = ? AND recorded_at BETWEEN ? AND ? ORDER BY recorded_at",
            TARGET_USER_ID,
            Timestamp.valueOf(FROM),
            Timestamp.valueOf(TO));

    String exercisePlan =
        explain(
            "SELECT id, exercise_type, started_at, ended_at FROM exercise_records "
                + "WHERE user_id = ? AND started_at <= ? AND ended_at >= ? ORDER BY started_at",
            TARGET_USER_ID,
            Timestamp.valueOf(TO),
            Timestamp.valueOf(FROM));

    String sleepPlan =
        explain(
            "SELECT id, started_at, ended_at FROM sleep_records "
                + "WHERE user_id = ? AND started_at <= ? AND ended_at >= ? ORDER BY started_at",
            TARGET_USER_ID,
            Timestamp.valueOf(TO),
            Timestamp.valueOf(FROM));

    System.out.println("=== EXPLAIN: glucose ===\n" + glucosePlan);
    System.out.println("=== EXPLAIN: meal ===\n" + mealPlan);
    System.out.println("=== EXPLAIN: exercise ===\n" + exercisePlan);
    System.out.println("=== EXPLAIN: sleep ===\n" + sleepPlan);

    assertThat(glucosePlan).contains("idx_glucose_user_time");
    assertThat(mealPlan).contains("idx_meal_user_time");
    assertThat(exercisePlan).contains("idx_ex_user_time");
    assertThat(sleepPlan).contains("idx_sleep_user_time");
  }

  // ── helpers ──────────────────────────────────────────────────

  private long[] runAllQueries() {
    long g =
        measure(
            () ->
                jdbc.query(
                    "SELECT id, value, measured_at FROM glucose_records "
                        + "WHERE user_id = ? AND measured_at BETWEEN ? AND ? ORDER BY measured_at",
                    (rs, n) -> rs.getLong("id"),
                    TARGET_USER_ID,
                    Timestamp.valueOf(FROM),
                    Timestamp.valueOf(TO)));
    long m =
        measure(
            () ->
                jdbc.query(
                    "SELECT id, food_id, recorded_at FROM meal_records "
                        + "WHERE user_id = ? AND recorded_at BETWEEN ? AND ? ORDER BY recorded_at",
                    (rs, n) -> rs.getLong("id"),
                    TARGET_USER_ID,
                    Timestamp.valueOf(FROM),
                    Timestamp.valueOf(TO)));
    long e =
        measure(
            () ->
                jdbc.query(
                    "SELECT id, exercise_type, started_at, ended_at FROM exercise_records "
                        + "WHERE user_id = ? AND started_at <= ? AND ended_at >= ? ORDER BY started_at",
                    (rs, n) -> rs.getLong("id"),
                    TARGET_USER_ID,
                    Timestamp.valueOf(TO),
                    Timestamp.valueOf(FROM)));
    long s =
        measure(
            () ->
                jdbc.query(
                    "SELECT id, started_at, ended_at FROM sleep_records "
                        + "WHERE user_id = ? AND started_at <= ? AND ended_at >= ? ORDER BY started_at",
                    (rs, n) -> rs.getLong("id"),
                    TARGET_USER_ID,
                    Timestamp.valueOf(TO),
                    Timestamp.valueOf(FROM)));
    return new long[] {g, m, e, s};
  }

  private long measure(Runnable r) {
    long start = System.nanoTime();
    r.run();
    return (System.nanoTime() - start) / 1_000_000;
  }

  private String explain(String sql, Object... args) {
    List<String> rows = jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS) " + sql, String.class, args);
    return String.join("\n", rows);
  }
}
