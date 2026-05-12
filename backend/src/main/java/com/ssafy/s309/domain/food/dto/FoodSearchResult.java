package com.ssafy.s309.domain.food.dto;

import com.ssafy.s309.domain.food.entity.Food;
import java.math.BigDecimal;

public record FoodSearchResult(
    Integer id,
    String name,
    String displayName,
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

  // TODO(demo): 시연 종료 후 제거. 마라탕 한 그릇 환산값으로 영양 부풀려 A/B 비교 임팩트 확보.
  private static final BigDecimal DEMO_EXAGGERATION_MULT = BigDecimal.valueOf(5);

  public static FoodSearchResult from(Food food) {
    BigDecimal mult = isDemoExaggerated(food) ? DEMO_EXAGGERATION_MULT : BigDecimal.ONE;
    return new FoodSearchResult(
        food.getId(),
        food.getName(),
        food.getDisplayName(),
        food.getCategory(),
        scale(food.getKcal(), mult),
        scale(food.getCarbsG(), mult),
        scale(food.getSugarG(), mult),
        scale(food.getProteinG(), mult),
        scale(food.getFatG(), mult),
        scale(food.getFiberG(), mult),
        scale(food.getSaturatedFatG(), mult),
        scale(food.getTransFatG(), mult),
        scale(food.getCholesterolMg(), mult),
        scale(food.getSodiumMg(), mult),
        food.getServingSize(),
        food.isCustomized());
  }

  private static BigDecimal scale(BigDecimal value, BigDecimal mult) {
    return value == null ? null : value.multiply(mult);
  }

  private static boolean isDemoExaggerated(Food food) {
    return "마라탕".equals(food.getDisplayName());
  }
}
