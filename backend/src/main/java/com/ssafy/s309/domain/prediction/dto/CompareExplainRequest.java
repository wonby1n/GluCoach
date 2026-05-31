package com.ssafy.s309.domain.prediction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompareExplainRequest(
    @NotBlank String foodAName,
    @NotBlank String foodBName,
    @NotNull Float foodAPeakMgdl,
    @NotNull Integer foodAPeakMinute,
    @NotNull Float foodASlope,
    @NotNull Float foodBPeakMgdl,
    @NotNull Integer foodBPeakMinute,
    @NotNull Float foodBSlope) {}
