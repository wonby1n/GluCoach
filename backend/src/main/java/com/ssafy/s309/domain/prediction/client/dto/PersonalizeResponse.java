package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PersonalizeResponse(
    String userId,
    String status,
    Integer nSamples,
    Double baseRmse30min,
    Double personalizedRmse30min,
    Double improvementPercent,
    String message) {}
