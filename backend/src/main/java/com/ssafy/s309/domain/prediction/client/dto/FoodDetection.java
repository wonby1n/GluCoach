package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * AI 음식 인식 결과 단일 항목.
 *
 * <p>{@code ignoreUnknown=true} — VISION 에픽의 AI 측 분류기 전환 머지 전까지 AI 가 여전히 {@code bbox} 등 추가 필드를 함께
 * 보낼 수 있어, 글로벌 Jackson 설정에 무관하게 unknown 필드를 무시하도록 명시.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FoodDetection(String nameKo, String nameEn, double confidence) {}
