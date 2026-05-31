package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import java.math.BigDecimal;

public record AgentUserProfileResponse(
    Integer userId,
    DiabetesType diabetesType,
    BigDecimal targetGlucoseMin,
    BigDecimal targetGlucoseMax) {

  public static AgentUserProfileResponse from(User user) {
    return new AgentUserProfileResponse(
        user.getId(), user.getDiabetesType(), user.getTargetLow(), user.getTargetHigh());
  }
}
