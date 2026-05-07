package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlucosePredictClientImpl implements GlucosePredictClient {

  private static final String PREDICT_PATH = "/inference/glucose/meal";
  static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  static final String CORRELATION_ID_MDC_KEY = "correlationId";
  static final int MAX_ATTEMPTS = 3;
  static final long INITIAL_BACKOFF_MS = 200L;
  static final double BACKOFF_MULTIPLIER = 2.0;

  private final RestClient aiRestClient;

  @Override
  public GlucosePredictResponse predict(GlucosePredictRequest request) {
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
          return doPredict(request, correlationId, attempt);
        } catch (AiServiceException e) {
          lastException = e;
          if (!isRetryable(e)) {
            throw e;
          }
          if (attempt < MAX_ATTEMPTS) {
            log.warn(
                "[{}] AI 호출 실패 (attempt={}/{}, errorType={}) — {}ms 후 재시도",
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

  GlucosePredictResponse doPredict(
      GlucosePredictRequest request, String correlationId, int attempt) {
    long startMs = System.currentTimeMillis();
    try {
      GlucosePredictResponse response =
          aiRestClient
              .post()
              .uri(PREDICT_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .header(CORRELATION_ID_HEADER, correlationId)
              .body(request)
              .retrieve()
              .body(GlucosePredictResponse.class);

      long elapsedMs = System.currentTimeMillis() - startMs;
      log.info("[{}] AI 호출 성공 (attempt={}, elapsedMs={})", correlationId, attempt, elapsedMs);

      if (response == null) {
        throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스로부터 빈 응답을 받았습니다");
      }
      return response;

    } catch (ResourceAccessException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      if (e.getCause() instanceof SocketTimeoutException) {
        log.warn("[{}] AI 호출 타임아웃 (attempt={}, elapsedMs={})", correlationId, attempt, elapsedMs);
        throw new AiServiceException(ErrorType.TIMEOUT, "AI 서비스 응답 시간 초과", e);
      }
      log.warn(
          "[{}] AI 호출 연결 실패 (attempt={}, elapsedMs={}): {}",
          correlationId,
          attempt,
          elapsedMs,
          e.getMessage());
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다", e);

    } catch (RestClientResponseException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      int status = e.getStatusCode().value();
      log.warn(
          "[{}] AI 호출 HTTP 오류 (attempt={}, elapsedMs={}, status={}): {}",
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

    } catch (RestClientException e) {
      // 응답 body 파싱 단계에서 발생한 예외 — Spring 6 RestClient 가 readBody 중 IOException 을 잡아
      // RestClientException("Error while extracting response...", cause) 로 래핑한다. SocketTimeout 이
      // 이 경로로 올라오면 기존 ResourceAccessException catch 에 잡히지 않아 retry 진입 못 함.
      throw mapRestClientException(e, correlationId, attempt, System.currentTimeMillis() - startMs);
    }
  }

  /** RestClient 가 던진 일반 RestClientException 을 root cause 기반으로 ErrorType 에 매핑. */
  AiServiceException mapRestClientException(
      RestClientException e, String correlationId, int attempt, long elapsedMs) {
    Throwable root = NestedExceptionUtils.getRootCause(e);
    // root 클래스명을 로그에 함께 남겨 다단 wrap 케이스에서 SocketTimeout/SocketException/EOFException 등 구분 가능.
    String rootName = root != null ? root.getClass().getSimpleName() : "none";
    if (root instanceof SocketTimeoutException) {
      log.warn(
          "[{}] AI 호출 응답 처리 타임아웃 (attempt={}, elapsedMs={}, root={})",
          correlationId,
          attempt,
          elapsedMs,
          rootName);
      return new AiServiceException(ErrorType.TIMEOUT, "AI 서비스 응답 처리 시간 초과", e);
    }
    if (root instanceof IOException) {
      log.warn(
          "[{}] AI 호출 응답 처리 I/O 실패 (attempt={}, elapsedMs={}, root={}): {}",
          correlationId,
          attempt,
          elapsedMs,
          rootName,
          e.getMessage());
      return new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스 응답 처리 실패", e);
    }
    log.warn(
        "[{}] AI 호출 처리 실패 (attempt={}, elapsedMs={}, root={}): {}",
        correlationId,
        attempt,
        elapsedMs,
        rootName,
        e.getMessage());
    return new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스 처리 실패", e);
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
