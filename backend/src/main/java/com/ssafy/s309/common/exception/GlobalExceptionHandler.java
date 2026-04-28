package com.ssafy.s309.common.exception;

import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .findFirst()
            .orElse("입력값이 올바르지 않습니다");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(AiServiceException.class)
  public ResponseEntity<Map<String, String>> handleAiService(AiServiceException e) {
    HttpStatus status =
        e.getErrorType() == ErrorType.INVALID_INPUT
            ? HttpStatus.BAD_REQUEST
            : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status)
        .body(Map.of("message", e.getMessage(), "errorType", e.getErrorType().name()));
  }
}
