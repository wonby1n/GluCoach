package com.ssafy.s309.domain.food.dto;

import com.ssafy.s309.domain.food.entity.Food;
import java.math.BigDecimal;

public record FoodSearchResult(
    Integer id,
    String name,
    BigDecimal kcal,
    BigDecimal carbsG,
    BigDecimal sugarG,
    BigDecimal proteinG,
    BigDecimal fatG) {

  public static FoodSearchResult from(Food food) {
    return new FoodSearchResult(
        food.getId(),
        food.getName(),
        food.getKcal(),
        food.getCarbsG(),
        food.getSugarG(),
        food.getProteinG(),
        food.getFatG());
  }
}
