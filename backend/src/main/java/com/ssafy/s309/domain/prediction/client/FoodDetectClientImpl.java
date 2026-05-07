package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodDetectClientImpl implements FoodDetectClient {

  private static final String DETECT_PATH = "/api/v1/food/detect";
  private static final String DEFAULT_FILENAME = "image.jpg";
  private static final String DEFAULT_CONTENT_TYPE = MediaType.IMAGE_JPEG_VALUE;
  static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  static final String CORRELATION_ID_MDC_KEY = "correlationId";
  static final int MAX_ATTEMPTS = 3;
  static final long INITIAL_BACKOFF_MS = 200L;
  static final double BACKOFF_MULTIPLIER = 2.0;

  private final RestClient aiRestClient;

  @Override
  public FoodDetectResponse detect(MultipartFile image) {
    byte[] bytes;
    try {
      bytes = image.getBytes();
    } catch (IOException e) {
      throw new AiServiceException(ErrorType.INVALID_INPUT, "이미지 데이터를 읽을 수 없습니다", e);
    }
    String filename =
        image.getOriginalFilename() != null && !image.getOriginalFilename().isBlank()
            ? image.getOriginalFilename()
            : DEFAULT_FILENAME;
    String contentType =
        image.getContentType() != null && !image.getContentType().isBlank()
            ? image.getContentType()
            : DEFAULT_CONTENT_TYPE;

    // MDC 소유권: 호출 전 비어있던 경우만 put + 종료 시점에 remove.
    // 외부에서 주입된 correlationId 는 보존 (Tomcat 스레드 풀 재사용 시 잔류값 방지 + 외부 컨텍스트 존중).
    String existing = MDC.get(CORRELATION_ID_MDC_KEY);
    boolean ownsCorrelationId = existing == null || existing.isBlank();
    String correlationId = ownsCorrelationId ? UUID.randomUUID().toString() : existing;
    if (ownsCorrelationId) {
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
    }

    try {
      long backoffMs = INITIAL_BACKOFF_MS;
      AiServiceException lastException = null;

      for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
          return doDetect(bytes, filename, contentType, correlationId, attempt);
        } catch (AiServiceException e) {
          lastException = e;
          if (!isRetryable(e)) {
            throw e;
          }
          if (attempt < MAX_ATTEMPTS) {
            log.warn(
                "[{}] AI 음식 인식 실패 (attempt={}/{}, errorType={}) — {}ms 후 재시도",
                correlationId,
                attempt,
                MAX_ATTEMPTS,
                e.getErrorType(),
                backoffMs);
            sleep(backoffMs);
            backoffMs = (long) (backoffMs * BACKOFF_MULTIPLIER);
          }
        }
      }
      throw lastException;
    } finally {
      if (ownsCorrelationId) {
        MDC.remove(CORRELATION_ID_MDC_KEY);
      }
    }
  }

  FoodDetectResponse doDetect(
      byte[] bytes, String filename, String contentType, String correlationId, int attempt) {
    long startMs = System.currentTimeMillis();
    try {
      FoodDetectResponse response =
          aiRestClient
              .post()
              .uri(DETECT_PATH)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .header(CORRELATION_ID_HEADER, correlationId)
              .body(buildMultipartBody(bytes, filename, contentType))
              .retrieve()
              .body(FoodDetectResponse.class);

      long elapsedMs = System.currentTimeMillis() - startMs;
      log.info("[{}] AI 음식 인식 성공 (attempt={}, elapsedMs={})", correlationId, attempt, elapsedMs);

      if (response == null) {
        throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스로부터 빈 응답을 받았습니다");
      }
      return response;

    } catch (ResourceAccessException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      if (e.getCause() instanceof SocketTimeoutException) {
        log.warn(
            "[{}] AI 음식 인식 타임아웃 (attempt={}, elapsedMs={})", correlationId, attempt, elapsedMs);
        throw new AiServiceException(ErrorType.TIMEOUT, "AI 서비스 응답 시간 초과", e);
      }
      log.warn(
          "[{}] AI 음식 인식 연결 실패 (attempt={}, elapsedMs={}): {}",
          correlationId,
          attempt,
          elapsedMs,
          e.getMessage());
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다", e);

    } catch (RestClientResponseException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      int status = e.getStatusCode().value();
      log.warn(
          "[{}] AI 음식 인식 HTTP 오류 (attempt={}, elapsedMs={}, status={}): {}",
          correlationId,
          attempt,
          elapsedMs,
          status,
          e.getMessage());
      if (status == 400) {
        throw new AiServiceException(
            ErrorType.INVALID_INPUT, "AI 서비스 입력 데이터 오류: " + e.getMessage(), e);
      }
      throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스 오류 (HTTP " + status + ")", e);
    }
  }

  private static MultiValueMap<String, HttpEntity<?>> buildMultipartBody(
      byte[] bytes, String filename, String contentType) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    ByteArrayResource resource =
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return filename;
          }
        };
    builder.part("file", resource).contentType(MediaType.parseMediaType(contentType));
    return builder.build();
  }

  private boolean isRetryable(AiServiceException e) {
    return e.getErrorType() == ErrorType.TIMEOUT
        || e.getErrorType() == ErrorType.SERVICE_UNAVAILABLE;
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "재시도 대기 중 인터럽트됨", ie);
    }
  }
}
