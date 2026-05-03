package com.ssafy.s309.domain.prediction.client.dto;

public record FoodNutrition(
    String foodId,
    String name,
    Double carbsG,
    Double proteinG,
    Double fatG,
    Double kcal,
    Double sugarG,
    Integer giScore) {}
