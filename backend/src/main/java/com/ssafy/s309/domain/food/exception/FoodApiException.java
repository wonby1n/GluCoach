package com.ssafy.s309.domain.food.exception;

public class FoodApiException extends RuntimeException {

  public FoodApiException(String message) {
    super(message);
  }

  public FoodApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
