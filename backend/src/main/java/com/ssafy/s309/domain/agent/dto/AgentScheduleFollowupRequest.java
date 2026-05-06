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
 *
 * <p>{@code reason}은 Agent의 LLM 판단 메모(예: "회의 중이라 못 움직임"). 현재 BE는 받기만 하고 저장하지 않음 — 폴러가 Agent 재호출 시 이
 * reason을 돌려주는 경로가 없고, Agent가 깨어나면 자체 데이터 조회 도구로 컨텍스트를 다시 구성하기 때문.
 */
public record AgentScheduleFollowupRequest(
    @NotNull Integer userId,
    @NotBlank @Size(max = 50) String triggerType,
    Integer referenceId,
    @NotNull @Min(1) @Max(1440) Integer delayMinutes,
    @Size(max = 500) String reason) {}
