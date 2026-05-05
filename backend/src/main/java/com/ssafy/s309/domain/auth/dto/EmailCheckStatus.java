package com.ssafy.s309.domain.auth.dto;

public enum EmailCheckStatus {
  AVAILABLE,
  ALREADY_REGISTERED,
  INVALID_FORMAT,
  RATE_LIMITED
}
