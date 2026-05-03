package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AgentSleepResponse(
    LocalDate date, Integer sleepMinutes, BigDecimal averageSleepMinutes) {}
