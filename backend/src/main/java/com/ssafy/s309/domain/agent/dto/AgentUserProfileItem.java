package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.user.entity.User;
import java.math.BigDecimal;

public record AgentUserProfileItem(
    Integer userId,
    Short age,
    String gender,
    BigDecimal height,
    BigDecimal weight,
    String diabetesType,
    Boolean isMedicated,
    BigDecimal targetLow,
    BigDecimal targetHigh) {

  public static AgentUserProfileItem from(User u) {
    return new AgentUserProfileItem(
        u.getId(),
        u.getAge(),
        u.getGender(),
        u.getHeight(),
        u.getWeight(),
        u.getDiabetesType() != null ? u.getDiabetesType().name() : null,
        u.getIsMedicated(),
        u.getTargetLow(),
        u.getTargetHigh());
  }
}
