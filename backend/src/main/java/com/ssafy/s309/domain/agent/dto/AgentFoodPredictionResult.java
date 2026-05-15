package com.ssafy.s309.domain.agent.dto;

/**
 * Agent 혈당 예측 결과 — LLM 판단 입력용으로 압축된 요약.
 *
 * <p>전체 curve 대신 peak/delta 만 노출. LLM이 "혈당 얼마로 올라요" / "안돼요" / "다른 거 어때요" 톤을 결정할 근거.
 *
 * <p>{@code riskLevel}: peak 가 200↑ "high", 180↑ "elevated", 그 외 "normal".
 */
public record AgentFoodPredictionResult(
    Integer foodId,
    String foodName,
    Double currentMgdl,
    Double peakMgdl,
    Integer peakMinute,
    Double deltaMgdl,
    String riskLevel) {}
