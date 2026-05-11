package com.ssafy.s309.domain.meal.dto;

import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.time.LocalDateTime;

public record MealRecordResponse(
    Integer mealId,
    Integer foodId,
    String foodName,
    String foodDisplayName,
    String memo,
    LocalDateTime recordedAt,
    String imageUrl) {

  public static MealRecordResponse from(MealRecord meal, String imageUrl) {
    return new MealRecordResponse(
        meal.getId(),
        meal.getFoodId(),
        meal.getFood() != null ? meal.getFood().getName() : null,
        meal.getFood() != null ? meal.getFood().getDisplayName() : null,
        meal.getMemo(),
        meal.getRecordedAt(),
        imageUrl);
  }
}
