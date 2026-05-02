package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;
import java.math.BigDecimal;

public record SettingsUpdateRequest(
    String name,
    Integer age,
    String gender,
    String phone,
    BigDecimal height,
    BigDecimal weight,
    DiabetesType diabetesType,
    Boolean isMedicated,
    BigDecimal targetLow,
    BigDecimal targetHigh,
    Integer weekStartDay) {}
