package com.ssafy.s309.domain.sleep.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record SleepSessionCreateRequest(
    @NotNull LocalDateTime startedAt,
    @NotNull LocalDateTime endedAt,
    @Size(max = 30) String source) {}
