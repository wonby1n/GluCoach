package com.ssafy.s309.domain.food.dto;

import com.ssafy.s309.domain.food.entity.Food;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

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
    BigDecimal demoMult = isDemoExaggerated(food) ? DEMO_EXAGGERATION_MULT : BigDecimal.ONE;
    BigDecimal servingMult =
        food.getServingSize() != null
            ? food.getServingSize().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
            : BigDecimal.ONE;
    BigDecimal mult = demoMult.multiply(servingMult, MathContext.DECIMAL64);
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
    // displayName 또는 raw name 에 "마라탕" 포함이면 매칭 — equals 는 NFC/NFD 정규화 차이,
    // trailing 공백, V18 정제 변형(예: "마라탕 (매운맛)") 등에 취약하므로 contains 로 완화.
    return (food.getDisplayName() != null && food.getDisplayName().contains("마라탕"))
        || (food.getName() != null && food.getName().contains("마라탕"));
  }
}
