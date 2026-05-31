package com.ssafy.s309.domain.prediction.exception;

public class AiServiceException extends RuntimeException {

  private final ErrorType errorType;

  public AiServiceException(ErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public AiServiceException(ErrorType errorType, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public enum ErrorType {
    TIMEOUT,
    MODEL_ERROR,
    INVALID_INPUT,
    SERVICE_UNAVAILABLE
  }
}
