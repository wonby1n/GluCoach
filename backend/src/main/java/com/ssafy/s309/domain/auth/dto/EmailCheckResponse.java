package com.ssafy.s309.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailCheckResponse(
    boolean available, EmailCheckStatus status, Integer retryAfterSeconds) {

  public static EmailCheckResponse ofAvailable() {
    return new EmailCheckResponse(true, EmailCheckStatus.AVAILABLE, null);
  }

  public static EmailCheckResponse ofAlreadyRegistered() {
    return new EmailCheckResponse(false, EmailCheckStatus.ALREADY_REGISTERED, null);
  }

  public static EmailCheckResponse ofInvalidFormat() {
    return new EmailCheckResponse(false, EmailCheckStatus.INVALID_FORMAT, null);
  }

  public static EmailCheckResponse ofRateLimited(int retryAfterSeconds) {
    return new EmailCheckResponse(false, EmailCheckStatus.RATE_LIMITED, retryAfterSeconds);
  }
}
