package com.ssafy.s309.domain.prediction.client.dto;

import java.util.List;

public record FoodDetectResponse(int count, List<FoodDetection> detections) {}
