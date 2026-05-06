package com.ssafy.s309.domain.agent.dto;

/**
 * BE → AI 서버 trigger 호출 payload.
 *
 * <p>{@code reason}은 schedule_followup 경로에서만 채워짐 (Agent의 LLM 판단 메모를 echo). post_meal 자동 예약 경로에서는
 * NULL.
 */
public record AgentTriggerDispatchRequest(
    Integer userId, String triggerType, Integer referenceId, String reason) {}
