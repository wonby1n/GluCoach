package com.ssafy.s309.domain.cgm.dto;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CgmRecordResponse(Long id, BigDecimal value, LocalDateTime measuredAt) {

  public static CgmRecordResponse from(GlucoseRecord r) {
    return new CgmRecordResponse(r.getId(), r.getValue(), r.getMeasuredAt());
  }
}
