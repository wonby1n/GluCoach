package com.ssafy.s309.domain.meal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodGradeResponse(
    Integer foodId,
    String foodName,
    String grade,
    BigDecimal avgSlope,
    Integer mealCount,
    LocalDateTime lastEatenAt) {}
