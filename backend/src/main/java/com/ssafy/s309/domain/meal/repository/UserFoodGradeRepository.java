package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.meal.dto.FoodGradeResponse;
import com.ssafy.s309.domain.meal.entity.UserFoodGrade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFoodGradeRepository extends JpaRepository<UserFoodGrade, Integer> {

  Optional<UserFoodGrade> findByUserIdAndFoodId(Integer userId, Integer foodId);

  @Query(
      """
      SELECT new com.ssafy.s309.domain.meal.dto.FoodGradeResponse(
          ufg.foodId, f.name, f.displayName, ufg.grade, ufg.avgSlope, ufg.mealCount, ufg.updatedAt)
      FROM UserFoodGrade ufg
      JOIN Food f ON ufg.foodId = f.id
      WHERE ufg.userId = :userId
      ORDER BY ufg.updatedAt DESC
      """)
  List<FoodGradeResponse> findGradesByUserId(@Param("userId") Integer userId);

  /** Agent food-recommend용 — 등급 + 사용자가 그 음식 마지막에 먹었을 때 사진 키. 사진 없으면 null. food_id별 최근 1장만. */
  @Query(
      value =
          """
          SELECT ufg.food_id        AS foodId,
                 f.name             AS foodName,
                 f.display_name     AS foodDisplayName,
                 ufg.grade          AS grade,
                 ufg.avg_slope      AS avgSlope,
                 ufg.meal_count     AS mealCount,
                 (SELECT mr.image_storage_key
                    FROM meal_records mr
                   WHERE mr.user_id = ufg.user_id
                     AND mr.food_id = ufg.food_id
                     AND mr.image_storage_key IS NOT NULL
                   ORDER BY mr.recorded_at DESC
                   LIMIT 1)         AS latestMealImageKey
            FROM user_food_grades ufg
            JOIN foods f ON f.id = ufg.food_id
           WHERE ufg.user_id = :userId
           ORDER BY ufg.updated_at DESC
          """,
      nativeQuery = true)
  List<AgentFoodGradeRow> findAgentGradesWithImageByUserId(@Param("userId") Integer userId);

  /** Spring Data 인터페이스 프로젝션. AgentUserDataService가 AgentFoodGradeItem으로 매핑. */
  interface AgentFoodGradeRow {
    Integer getFoodId();

    String getFoodName();

    String getFoodDisplayName();

    String getGrade();

    java.math.BigDecimal getAvgSlope();

    Integer getMealCount();

    String getLatestMealImageKey();
  }
}
