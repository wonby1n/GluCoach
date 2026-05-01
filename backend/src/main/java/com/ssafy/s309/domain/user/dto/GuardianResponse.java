package com.ssafy.s309.domain.user.dto;

import com.ssafy.s309.domain.user.entity.WardGuardian;

public record GuardianResponse(
    Long id, Long wardId, Long guardianId, String relation, Integer priority) {

  public static GuardianResponse from(WardGuardian wg) {
    return new GuardianResponse(
        wg.getId(),
        wg.getWard().getId(),
        wg.getGuardian().getId(),
        wg.getRelation(),
        wg.getPriority());
  }
}
