package com.ssafy.s309.domain.prediction.client.dto;

import java.util.List;

/**
 * AI 음식 인식 응답 ({@code POST /api/v1/food/detect}).
 *
 * <p>{@code detections} 는 AI 측에서 confidence DESC 로 정렬된 상태로 반환된다 (ai/app/models/food_detector.py 의
 * {@code detections.sort(key=lambda d: d["confidence"], reverse=True)}). 호출자는 {@code
 * detections.get(0)} 을 top 으로 사용 가능.
 */
public record FoodDetectResponse(int count, List<FoodDetection> detections) {}
