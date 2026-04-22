package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import java.util.UUID;

public record SettingsResponse(
    UUID userId,
    Float height,
    Float weight,
    DiabetesType diabetesType,
    Boolean isMedicated,
    Integer targetLow,
    Integer targetHigh,
    Integer alertLow,
    Integer alertHigh,
    Boolean nightWatch,
    String characterType) {

  public static SettingsResponse from(User user) {
    return new SettingsResponse(
        user.getUserId(),
        user.getHeight(),
        user.getWeight(),
        user.getDiabetesType(),
        user.getIsMedicated(),
        user.getTargetLow(),
        user.getTargetHigh(),
        user.getAlertLow(),
        user.getAlertHigh(),
        user.getNightWatch(),
        user.getCharacterType());
  }
}
