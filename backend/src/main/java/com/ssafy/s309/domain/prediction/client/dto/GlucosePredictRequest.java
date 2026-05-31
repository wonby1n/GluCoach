package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GlucosePredictRequest(
    String userId, List<Double> recentValues, MealInfo meal, UserProfileWithPattern userProfile) {}
