package com.ssafy.s309.domain.health.dto;

import com.ssafy.s309.domain.health.entity.DailyHealthSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DailyHealthSummaryResponse(
    LocalDate date,
    Integer steps,
    BigDecimal caloriesBurned,
    Integer sleepMinutes,
    BigDecimal avgHeartRate,
    LocalDateTime updatedAt) {

  public static DailyHealthSummaryResponse from(DailyHealthSummary e) {
    return new DailyHealthSummaryResponse(
        e.getDate(),
        e.getSteps(),
        e.getCaloriesBurned(),
        e.getSleepMinutes(),
        e.getAvgHeartRate(),
        e.getUpdatedAt());
  }
}
