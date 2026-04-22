package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;

public record SettingsUpdateRequest(
    Float height,
    Float weight,
    DiabetesType diabetesType,
    Boolean isMedicated,
    Integer targetLow,
    Integer targetHigh,
    Integer alertLow,
    Integer alertHigh,
    Boolean nightWatch,
    String characterType) {}
