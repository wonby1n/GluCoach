package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AgentGlucosePoint(LocalDateTime timestamp, BigDecimal value) {

  public static AgentGlucosePoint from(GlucoseRecord r) {
    return new AgentGlucosePoint(r.getMeasuredAt(), r.getValue());
  }
}
