package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;

public record AgentFoodGradeItem(
    Integer foodId,
    String foodName,
    String foodDisplayName,
    String grade,
    BigDecimal avgSlope,
    Integer mealCount,
    String latestMealImageKey) {}
