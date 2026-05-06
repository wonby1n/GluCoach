package com.ssafy.s309.domain.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Agent #8 — schedule_followup: Agent → BE: POST /api/agent/schedule_followup body.
 *
 * <p>Agent가 LangChain tool로 호출. {@code delayMinutes} 후 {@link
 * com.ssafy.s309.domain.agent.scheduler.AgentTriggerScheduler}가 자동 발화한다.
 */
public record AgentScheduleFollowupRequest(
    @NotNull Integer userId,
    @NotBlank @Size(max = 50) String triggerType,
    Integer referenceId,
    @NotNull @Min(1) @Max(1440) Integer delayMinutes) {}
