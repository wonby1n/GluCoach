package com.ssafy.s309.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record GuardianRequest(@NotNull Long guardianId, String relation) {}
