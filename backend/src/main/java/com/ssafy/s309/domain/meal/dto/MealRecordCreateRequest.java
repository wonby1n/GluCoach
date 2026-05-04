package com.ssafy.s309.domain.meal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record MealRecordCreateRequest(
    @NotNull Integer foodId, @NotNull LocalDateTime recordedAt, @Size(max = 255) String memo) {}
