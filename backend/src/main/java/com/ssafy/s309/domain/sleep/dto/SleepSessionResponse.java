package com.ssafy.s309.domain.sleep.dto;

import com.ssafy.s309.domain.sleep.entity.SleepSession;
import java.time.LocalDateTime;

public record SleepSessionResponse(
    Integer id, LocalDateTime startedAt, LocalDateTime endedAt, String source) {

  public static SleepSessionResponse from(SleepSession s) {
    return new SleepSessionResponse(s.getId(), s.getStartedAt(), s.getEndedAt(), s.getSource());
  }
}
