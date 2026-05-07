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
          ufg.foodId, f.name, ufg.grade, ufg.avgSlope, ufg.mealCount, ufg.updatedAt)
      FROM UserFoodGrade ufg
      JOIN Food f ON ufg.foodId = f.id
      WHERE ufg.userId = :userId
      ORDER BY ufg.updatedAt DESC
      """)
  List<FoodGradeResponse> findGradesByUserId(@Param("userId") Integer userId);
}
