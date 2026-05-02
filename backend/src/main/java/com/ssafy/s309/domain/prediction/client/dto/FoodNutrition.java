package com.ssafy.s309.domain.prediction.client.dto;

import java.math.BigDecimal;

public record FoodNutrition(
    String foodId,
    String name,
    BigDecimal carbsG,
    BigDecimal proteinG,
    BigDecimal fatG,
    BigDecimal kcal,
    BigDecimal sugarG,
    Integer giScore) {}
