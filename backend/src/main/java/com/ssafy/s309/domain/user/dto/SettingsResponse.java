package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import java.math.BigDecimal;

public record SettingsResponse(
    Integer userId,
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
    Integer weekStartDay) {

  public static SettingsResponse from(User user) {
    return new SettingsResponse(
        user.getId(),
        user.getName(),
        user.getAge(),
        user.getGender(),
        user.getPhone(),
        user.getHeight(),
        user.getWeight(),
        user.getDiabetesType(),
        user.getIsMedicated(),
        user.getTargetLow(),
        user.getTargetHigh(),
        user.getWeekStartDay());
  }
}
