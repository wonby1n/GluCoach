package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.Guardian;
import java.util.UUID;

public record GuardianResponse(
    UUID guardianId,
    String name,
    String phone,
    String relation,
    Boolean isPrimary,
    Integer priority) {

  public static GuardianResponse from(Guardian guardian) {
    return new GuardianResponse(
        guardian.getGuardianId(),
        guardian.getName(),
        guardian.getPhone(),
        guardian.getRelation(),
        guardian.getIsPrimary(),
        guardian.getPriority());
  }
}
