package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;

/** Agent 음식 추천용 — 사용자가 안 먹어본 음식 후보. food_id는 항상 채워져서 반환. */
public record AgentUnseenFoodItem(
    Integer foodId, String name, String category, BigDecimal kcal, BigDecimal carbsG) {}
