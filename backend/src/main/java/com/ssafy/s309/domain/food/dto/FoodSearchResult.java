package com.ssafy.s309.domain.food.dto;

import com.ssafy.s309.domain.food.entity.Food;
import java.math.BigDecimal;

public record FoodSearchResult(
    Integer id,
    String name,
    String category,
    BigDecimal kcal,
    BigDecimal carbsG,
    BigDecimal sugarG,
    BigDecimal proteinG,
    BigDecimal fatG,
    BigDecimal fiberG,
    BigDecimal saturatedFatG,
    BigDecimal transFatG,
    BigDecimal cholesterolMg,
    BigDecimal sodiumMg,
    BigDecimal servingSize,
    boolean isCustomized) {

  public static FoodSearchResult from(Food food) {
    return new FoodSearchResult(
        food.getId(),
        food.getName(),
        food.getCategory(),
        food.getKcal(),
        food.getCarbsG(),
        food.getSugarG(),
        food.getProteinG(),
        food.getFatG(),
        food.getFiberG(),
        food.getSaturatedFatG(),
        food.getTransFatG(),
        food.getCholesterolMg(),
        food.getSodiumMg(),
        food.getServingSize(),
        food.isCustomized());
  }
}
