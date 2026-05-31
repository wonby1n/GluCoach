package com.ssafy.s309.domain.timeline.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExercisePin(
    Integer id,
    String exerciseType,
    BigDecimal calories,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {}
