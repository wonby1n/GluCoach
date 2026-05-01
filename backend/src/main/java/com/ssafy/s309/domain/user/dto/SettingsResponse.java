package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;

public record SettingsResponse(
    Long userId,
    String name,
    Short age,
    String gender,
    String phone,
    Float height,
    Float weight,
    DiabetesType diabetesType,
    Boolean isMedicated,
    Integer targetLow,
    Integer targetHigh,
    Short weekStartDay) {

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
