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

  /**
   * Agent #4 meals: MealRecord.food (@ManyToOne readonly) 관계로 정통 JPQL. ERD(real_end(MVP).sql) 기준
   * food_id NOT NULL이라 inner join 안전. constructor projection으로 단건당 7필드 한 쿼리로 매핑.
   */
  @Query(
      """
      SELECT new com.ssafy.s309.domain.agent.dto.AgentMealItem(
        m.id, m.recordedAt, m.food.name, m.food.carbsG, m.food.proteinG, m.food.fatG, m.food.kcal
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
}
