package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;

public record SettingsUpdateRequest(
    String name,
    Integer age,
    String gender,
    String phone,
    Float height,
    Float weight,
    DiabetesType diabetesType,
    Boolean isMedicated,
    Integer targetLow,
    Integer targetHigh,
    Integer weekStartDay) {}
