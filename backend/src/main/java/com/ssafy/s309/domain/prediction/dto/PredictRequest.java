package com.ssafy.s309.domain.prediction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PredictRequest(
    Integer foodId,
    @NotBlank String foodName,
    @NotNull Double carbsG,
    @NotNull Double proteinG,
    @NotNull Double fatG,
    @NotNull Double kcal,
    Double sugarG,
    Integer giScore) {}
