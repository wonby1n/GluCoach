package com.ssafy.s309.domain.timeline.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TimelineResponse(
    String range,
    LocalDateTime from,
    LocalDateTime to,
    List<GlucosePoint> glucosePoints,
    List<MealPin> meals,
    List<ExercisePin> exercises,
    List<SleepPin> sleeps) {}
