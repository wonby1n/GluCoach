package com.ssafy.s309.domain.agent.dto;

public record AgentTriggerDispatchRequest(
    Integer userId, String triggerType, Integer referenceId) {}
