package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MealHistoryItem(
    Double carbs,
    Double proteinG,
    Double fatG,
    Double fiberG,
    Double kcal,
    String mealTimeIso,
    Double currentGlucose,
    List<Double> bgCurve) {}
