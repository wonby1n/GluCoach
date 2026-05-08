package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentMealItem(
    Integer mealId,
    LocalDateTime timestamp,
    String foodName,
    BigDecimal carbs,
    BigDecimal protein,
    BigDecimal fat,
    BigDecimal calories,
    String imageStorageKey) {}
