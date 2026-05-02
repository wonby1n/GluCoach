package com.ssafy.s309.domain.timeline.dto;

import com.ssafy.s309.domain.health.entity.ExerciseRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExercisePin(
    Integer id,
    String exerciseType,
    BigDecimal calories,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {

  public static ExercisePin from(ExerciseRecord r) {
    return new ExercisePin(
        r.getId(), r.getExerciseType(), r.getCalories(), r.getStartedAt(), r.getEndedAt());
  }
}
