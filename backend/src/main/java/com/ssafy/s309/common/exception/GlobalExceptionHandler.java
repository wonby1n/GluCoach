package com.ssafy.s309.common.exception;

import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
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

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, String>> handleConstraintViolation(
      ConstraintViolationException e) {
    String message =
        e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .findFirst()
            .orElse("입력값이 올바르지 않습니다");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, String>> handleMissingParam(
      MissingServletRequestParameterException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("message", e.getParameterName() + " 파라미터가 누락되었습니다"));
  }

  @ExceptionHandler(FoodApiException.class)
  public ResponseEntity<Map<String, String>> handleFoodApi(FoodApiException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("message", e.getMessage()));
  }

  /**
   * DB 제약(CHECK / UNIQUE / FK / NOT NULL) 위반.
   *
   * <p>핸들러가 없으면 Spring Security 필터까지 예외가 거꾸로 올라가 401로 잘못 응답되는 사이드 케이스가 있어 명시 처리. 클라엔 generic 메시지만
   * 노출(스키마 누설 방지), 상세는 서버 로그.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrity(
      DataIntegrityViolationException e) {
    log.warn("DB integrity violation: {}", e.getMostSpecificCause().getMessage());
    return ResponseEntity.badRequest().body(Map.of("message", "데이터 제약 위반"));
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
