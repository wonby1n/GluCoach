package com.ssafy.s309.domain.auth.exception;

public class RateLimitedException extends RuntimeException {

  private final int retryAfterSeconds;

  public RateLimitedException(int retryAfterSeconds) {
    super("Too many requests");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
