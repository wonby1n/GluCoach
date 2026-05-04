package com.ssafy.s309.domain.agent.dto;

import java.time.LocalDateTime;

public record AgentStepResponse(
    Integer userId, int windowSteps, LocalDateTime from, LocalDateTime to) {}
