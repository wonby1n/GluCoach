package com.ssafy.s309.domain.meal.dto;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MealRecordResponse(
    Integer mealId,
    Integer foodId,
    String foodName,
    String foodDisplayName,
    String memo,
    LocalDateTime recordedAt,
    String imageUrl,
    BigDecimal kcal,
    BigDecimal carbsG,
    BigDecimal proteinG,
    BigDecimal fatG) {

  public static MealRecordResponse from(MealRecord meal, String imageUrl) {
    Food food = meal.getFood();
    return new MealRecordResponse(
        meal.getId(),
        meal.getFoodId(),
        food != null ? food.getName() : null,
        food != null ? food.getDisplayName() : null,
        meal.getMemo(),
        meal.getRecordedAt(),
        imageUrl,
        food != null ? food.getKcal() : null,
        food != null ? food.getCarbsG() : null,
        food != null ? food.getProteinG() : null,
        food != null ? food.getFatG() : null);
  }
}
