package com.ssafy.s309.domain.timeline.dto;

import java.time.LocalDateTime;

public enum TimelineRange {
  D1("1d", 1),
  D7("7d", 7),
  D30("30d", 30);

  private final String token;
  private final int days;

  TimelineRange(String token, int days) {
    this.token = token;
    this.days = days;
  }

  public String token() {
    return token;
  }

  public int days() {
    return days;
  }

  public LocalDateTime computeFrom(LocalDateTime to) {
    return to.minusDays(days);
  }

  public static TimelineRange parse(String input) {
    if (input == null || input.isBlank()) {
      return D1;
    }
    String normalized = input.trim().toLowerCase();
    for (TimelineRange r : values()) {
      if (r.token.equals(normalized)) {
        return r;
      }
    }
    throw new IllegalArgumentException("지원하지 않는 range 값: " + input + " (허용: 1d / 7d / 30d)");
  }
}
