package com.ssafy.s309.domain.health.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyHealthSummaryUpsertRequest(
    @NotNull LocalDate date,
    Integer steps,
    BigDecimal caloriesBurned,
    Integer sleepMinutes,
    BigDecimal avgHeartRate) {}
