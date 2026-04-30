package com.ssafy.s309.domain.food.repository;

import com.ssafy.s309.domain.food.entity.Food;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {

  Optional<Food> findByFoodApiId(String foodApiId);

  List<Food> findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
      String name, LocalDateTime cachedAtAfter);
}
