package com.ssafy.s309.domain.timeline.dto;

import com.ssafy.s309.domain.health.entity.SleepRecord;
import java.time.LocalDateTime;

public record SleepPin(Integer id, LocalDateTime startedAt, LocalDateTime endedAt) {

  public static SleepPin from(SleepRecord r) {
    return new SleepPin(r.getId(), r.getStartedAt(), r.getEndedAt());
  }
}
