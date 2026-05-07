package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FoodDetection(String nameKo, String nameEn, double confidence) {}
