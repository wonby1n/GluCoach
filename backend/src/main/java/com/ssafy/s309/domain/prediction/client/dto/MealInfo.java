package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MealInfo(
    Double carbs, String timeIso, Double proteinG, Double fatG, Double fiberG, Double kcal) {}
