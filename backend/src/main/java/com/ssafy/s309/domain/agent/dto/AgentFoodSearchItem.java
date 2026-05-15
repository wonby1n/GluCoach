package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;

/**
 * Agent 음식 검색 결과 — 자유 발화 모드에서 사용자가 말한 음식명으로 food_id를 찾기 위함.
 *
 * <p>STT 발화 "나 짬뽕 먹을거야" → agent가 search_food_by_name("짬뽕") → 후보 리스트 → 첫 후보의 foodId를 predict 도구에 전달.
 */
public record AgentFoodSearchItem(
    Integer foodId,
    String name,
    String displayName,
    String category,
    BigDecimal kcal,
    BigDecimal carbsG) {}
