package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.meal.entity.MealGlucoseResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealGlucoseResponseRepository extends JpaRepository<MealGlucoseResponse, Integer> {

  boolean existsByMealId(Integer mealId);
}
