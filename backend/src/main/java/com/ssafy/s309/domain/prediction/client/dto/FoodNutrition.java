package com.ssafy.s309.domain.prediction.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FoodNutrition(
    @NotBlank String foodId,
    @NotBlank String name,
    @NotNull Double carbsG,
    @NotNull Double proteinG,
    @NotNull Double fatG,
    @NotNull Double kcal,
    Double sugarG,
    Integer giScore) {}
