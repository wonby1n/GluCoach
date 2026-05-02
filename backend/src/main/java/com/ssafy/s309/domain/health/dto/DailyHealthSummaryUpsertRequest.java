package com.ssafy.s309.domain.health.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyHealthSummaryUpsertRequest(
    @NotNull LocalDate date,
    Integer steps,
    BigDecimal caloriesBurned,
    LocalDateTime sleepStart,
    LocalDateTime sleepEnd,
    Integer sleepMinutes,
    BigDecimal avgHeartRate) {}
