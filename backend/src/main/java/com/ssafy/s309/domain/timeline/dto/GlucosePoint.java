package com.ssafy.s309.domain.timeline.dto;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GlucosePoint(LocalDateTime measuredAt, BigDecimal value) {

  public static GlucosePoint from(GlucoseRecord r) {
    return new GlucosePoint(r.getMeasuredAt(), r.getValue());
  }
}
