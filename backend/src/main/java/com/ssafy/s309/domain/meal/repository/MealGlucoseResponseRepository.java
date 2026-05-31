package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.meal.entity.MealGlucoseResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealGlucoseResponseRepository extends JpaRepository<MealGlucoseResponse, Integer> {

  boolean existsByMealId(Integer mealId);

  /**
   * 캘린더 UI 용 — mealId 묶음에 대해 peak 혈당 값을 한 번에 조회한다. 기존엔 FE 가 식사별로 raw 측정값을 따로 받아 max 를 계산하느라 N+1 호출이
   * 일어났음. 스케줄러가 이미 계산해둔 peakGlucoseId 를 join 으로 끄집어낸다.
   */
  @Query(
      value =
          """
          SELECT mgr.meal_id AS mealId,
                 gr.value AS peakValue
          FROM meal_glucose_responses mgr
          JOIN glucose_records gr ON gr.id = mgr.peak_glucose_id
          WHERE mgr.meal_id IN (:mealIds)
          """,
      nativeQuery = true)
  List<MealPeakProjection> findPeaksByMealIds(@Param("mealIds") List<Integer> mealIds);

  interface MealPeakProjection {
    Integer getMealId();

    BigDecimal getPeakValue();
  }
}
