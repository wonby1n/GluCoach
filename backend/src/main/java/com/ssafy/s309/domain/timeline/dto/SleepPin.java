package com.ssafy.s309.domain.timeline.dto;

import java.time.LocalDateTime;

public record SleepPin(Integer id, LocalDateTime startedAt, LocalDateTime endedAt) {}
