package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.PersonalizeRequest;
import com.ssafy.s309.domain.prediction.client.dto.PersonalizeResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GlucosePersonalizeClientImpl implements GlucosePersonalizeClient {

  private static final String PERSONALIZE_PATH = "/inference/glucose/personalize";
  static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  static final String CORRELATION_ID_MDC_KEY = "correlationId";

  private final RestClient aiPersonalizeRestClient;

  public GlucosePersonalizeClientImpl(
      @Qualifier("aiPersonalizeRestClient") RestClient aiPersonalizeRestClient) {
    this.aiPersonalizeRestClient = aiPersonalizeRestClient;
  }

  @Override
  public PersonalizeResponse personalize(PersonalizeRequest request) {
    // MDC 소유권: 호출 전 비어있던 경우만 put + 종료 시점에 remove.
    String existing = MDC.get(CORRELATION_ID_MDC_KEY);
    boolean ownsCorrelationId = existing == null || existing.isBlank();
    String correlationId = ownsCorrelationId ? UUID.randomUUID().toString() : existing;
    if (ownsCorrelationId) {
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
    }

    long startMs = System.currentTimeMillis();
    try {
      PersonalizeResponse response =
          aiPersonalizeRestClient
              .post()
              .uri(PERSONALIZE_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .header(CORRELATION_ID_HEADER, correlationId)
              .body(request)
              .retrieve()
              .body(PersonalizeResponse.class);

      long elapsedMs = System.currentTimeMillis() - startMs;
      log.info("[{}] personalize 호출 성공 (elapsedMs={})", correlationId, elapsedMs);

      if (response == null) {
        throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스로부터 빈 응답을 받았습니다");
      }
      return response;

    } catch (ResourceAccessException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      if (e.getCause() instanceof SocketTimeoutException) {
        log.warn("[{}] personalize 타임아웃 (elapsedMs={})", correlationId, elapsedMs);
        throw new AiServiceException(ErrorType.TIMEOUT, "AI 학습 응답 시간 초과", e);
      }
      log.warn(
          "[{}] personalize 연결 실패 (elapsedMs={}): {}", correlationId, elapsedMs, e.getMessage());
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다", e);

    } catch (RestClientResponseException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      int status = e.getStatusCode().value();
      log.warn(
          "[{}] personalize HTTP 오류 (elapsedMs={}, status={}): {}",
          correlationId,
          elapsedMs,
          status,
          e.getMessage());
      if (status == 400) {
        throw new AiServiceException(
            ErrorType.INVALID_INPUT, "AI 학습 입력 데이터 오류: " + e.getMessage(), e);
      }
      if (status == 503) {
        throw new AiServiceException(
            ErrorType.SERVICE_UNAVAILABLE, "AI 학습 서비스 일시 사용 불가: " + e.getMessage(), e);
      }
      throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 학습 오류 (HTTP " + status + ")", e);

    } catch (RestClientException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      Throwable root = NestedExceptionUtils.getRootCause(e);
      String rootName = root != null ? root.getClass().getSimpleName() : "none";
      if (root instanceof SocketTimeoutException) {
        log.warn(
            "[{}] personalize 응답 처리 타임아웃 (elapsedMs={}, root={})",
            correlationId,
            elapsedMs,
            rootName);
        throw new AiServiceException(ErrorType.TIMEOUT, "AI 학습 응답 처리 시간 초과", e);
      }
      if (root instanceof IOException) {
        log.warn(
            "[{}] personalize 응답 처리 I/O 실패 (elapsedMs={}, root={}): {}",
            correlationId,
            elapsedMs,
            rootName,
            e.getMessage());
        throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 학습 응답 처리 실패", e);
      }
      log.warn(
          "[{}] personalize 처리 실패 (elapsedMs={}, root={}): {}",
          correlationId,
          elapsedMs,
          rootName,
          e.getMessage());
      throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 학습 처리 실패", e);

    } finally {
      if (ownsCorrelationId) {
        MDC.remove(CORRELATION_ID_MDC_KEY);
      }
    }
  }
}
