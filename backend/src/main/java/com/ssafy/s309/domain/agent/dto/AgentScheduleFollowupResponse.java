package com.ssafy.s309.domain.agent.dto;

import java.time.LocalDateTime;

public record AgentScheduleFollowupResponse(Integer triggerId, LocalDateTime scheduledAt) {}
