package com.ssafy.s309.domain.prediction.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserProfile(
    @NotBlank String diabetesType,
    @NotNull Boolean isMedicated,
    Float height,
    Float weight,
    Double currentGlucose,
    Double riseRate,
    Double fallRate,
    Double spikeThreshold) {}
