package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyFoodItemProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealRecordRepository extends JpaRepository<MealRecord, Integer> {

  List<MealRecord> findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
      Integer userId, LocalDateTime from, LocalDateTime to);

  @Query(
      """
      SELECT m FROM MealRecord m
      LEFT JOIN FETCH m.food
      WHERE m.userId = :userId
        AND m.recordedAt BETWEEN :from AND :to
      ORDER BY m.recordedAt ASC
      """)
  List<MealRecord> findWithFoodByUserIdAndRecordedAtBetween(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      """
      SELECT m FROM MealRecord m
      LEFT JOIN FETCH m.food
      WHERE m.userId = :userId
        AND m.foodId = :foodId
      ORDER BY m.recordedAt DESC
      """)
  List<MealRecord> findWithFoodByUserIdAndFoodId(
      @Param("userId") Integer userId, @Param("foodId") Integer foodId);

  /**
   * Agent #4 meals: MealRecord.food (@ManyToOne readonly) 관계로 정통 JPQL. ERD(real_end(MVP).sql) 기준
   * food_id NOT NULL이라 inner join 안전. constructor projection으로 단건당 7필드 한 쿼리로 매핑.
   */
  @Query(
      """
      SELECT new com.ssafy.s309.domain.agent.dto.AgentMealItem(
        m.id, m.recordedAt, m.food.name, m.food.displayName,
        m.food.carbsG, m.food.proteinG, m.food.fatG, m.food.kcal,
        m.imageStorageKey
      )
      FROM MealRecord m
      WHERE m.userId = :userId
        AND m.recordedAt BETWEEN :from AND :to
      ORDER BY m.recordedAt ASC
      """)
  List<AgentMealItem> findAgentMealsByUserAndRange(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT m FROM MealRecord m WHERE m.isProcessed = false AND m.recordedAt BETWEEN :expiry AND :cutoff")
  List<MealRecord> findUnprocessedBetween(
      @Param("expiry") LocalDateTime expiry, @Param("cutoff") LocalDateTime cutoff);

  /**
   * 캘린더 화면용 — 해당 월에 식사 기록이 존재하는 day-of-month 집합. FE 가 식사 있는 날짜에 점을 찍기 위해 사용. 기존엔 30일치를 매일 1번씩 burst
   * 조회해 nginx api_limit 에 걸렸음. native SQL 로 한 번에 distinct day 만 가져온다.
   */
  @Query(
      value =
          """
          SELECT DISTINCT EXTRACT(DAY FROM recorded_at)::int AS day
          FROM meal_records
          WHERE user_id = :userId
            AND recorded_at >= :from
            AND recorded_at < :to
          ORDER BY day
          """,
      nativeQuery = true)
  List<Integer> findDistinctDaysInRange(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  java.util.Optional<MealRecord> findFirstByUserIdOrderByRecordedAtDesc(Integer userId);

  @Query(
      "SELECT COUNT(m) FROM MealRecord m"
          + " WHERE m.userId = :userId"
          + " AND m.recordedAt >= :from AND m.recordedAt < :to")
  long countByUserIdAndRecordedAtBetween(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      value =
          """
          SELECT f.id         AS food_id,
                 f.name       AS food_name,
                 ufg.avg_slope AS avg_slope
          FROM meal_records mr
          JOIN foods f ON mr.food_id = f.id
          JOIN user_food_grades ufg ON ufg.user_id = :userId AND ufg.food_id = f.id
          WHERE mr.user_id = :userId
            AND mr.recorded_at >= :from
            AND mr.recorded_at < :to
            AND mr.food_id IS NOT NULL
            AND ufg.grade IN ('S', 'A', 'B')
          GROUP BY f.id, f.name, ufg.avg_slope
          ORDER BY ufg.avg_slope ASC
          LIMIT 5
          """,
      nativeQuery = true)
  List<WeeklyFoodItemProjection> findWeeklyGoodFoods(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      value =
          """
          SELECT f.id         AS food_id,
                 f.name       AS food_name,
                 ufg.avg_slope AS avg_slope
          FROM meal_records mr
          JOIN foods f ON mr.food_id = f.id
          JOIN user_food_grades ufg ON ufg.user_id = :userId AND ufg.food_id = f.id
          WHERE mr.user_id = :userId
            AND mr.recorded_at >= :from
            AND mr.recorded_at < :to
            AND mr.food_id IS NOT NULL
            AND ufg.grade IN ('C', 'D')
          GROUP BY f.id, f.name, ufg.avg_slope
          ORDER BY ufg.avg_slope DESC
          LIMIT 5
          """,
      nativeQuery = true)
  List<WeeklyFoodItemProjection> findWeeklyBadFoods(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
