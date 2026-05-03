package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealRecordRepository extends JpaRepository<MealRecord, Integer> {

  List<MealRecord> findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
      Integer userId, LocalDateTime from, LocalDateTime to);

  /**
   * Agent #4 meals: meal_records ⨝ foods (theta join, foodId 매개) constructor projection으로 단건당 7필드만
   * 한 쿼리로 매핑. food_id가 null/매칭 안 되는 meal은 자동 제외 (theta join 특성).
   */
  @Query(
      """
      SELECT new com.ssafy.s309.domain.agent.dto.AgentMealItem(
        m.id, m.recordedAt, f.name, f.carbsG, f.proteinG, f.fatG, f.kcal
      )
      FROM MealRecord m, com.ssafy.s309.domain.food.entity.Food f
      WHERE m.foodId = f.id
        AND m.userId = :userId
        AND m.recordedAt BETWEEN :from AND :to
      ORDER BY m.recordedAt ASC
      """)
  List<AgentMealItem> findAgentMealsByUserAndRange(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
