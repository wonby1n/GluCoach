package com.ssafy.s309.domain.prediction.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.MealInfo;
import com.ssafy.s309.domain.prediction.client.dto.UserProfileWithPattern;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GlucosePredictClientImplTest {

  @Mock private RestClient restClient;

  private GlucosePredictRequest request;
  private GlucosePredictResponse response;

  /** 헬퍼 메서드 단위 테스트(spy 불필요)에서 사용하는 plain 인스턴스. spy 가 필요한 테스트는 각자 spy(new ...) 그대로. */
  private GlucosePredictClientImpl helperClient;

  @BeforeEach
  void setUp() {
    MealInfo meal = new MealInfo(56.0, "2026-05-06T10:00:00");
    UserProfileWithPattern profile =
        new UserProfileWithPattern(100.0, 70.0, "medium", "T2D", "regular_3");
    request = new GlucosePredictRequest("1", List.of(100.0), meal, profile);
    response = new GlucosePredictResponse(List.of(), 168.0, 45, "base", 0.82);
    helperClient = new GlucosePredictClientImpl(restClient);
    MDC.remove(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY);
  }

  @Test
  void 첫_시도_성공_시_재시도_없음() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doReturn(response).when(client).doPredict(any(), anyString(), anyInt());

    GlucosePredictResponse result = client.predict(request);

    assertThat(result).isEqualTo(response);
    verify(client, times(1)).doPredict(any(), anyString(), anyInt());
  }

  @Test
  void 타임아웃_후_재시도_성공() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.TIMEOUT, "타임아웃"))
        .doReturn(response)
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    GlucosePredictResponse result = client.predict(request);

    assertThat(result).isEqualTo(response);
    verify(client, times(2)).doPredict(any(), anyString(), anyInt());
  }

  @Test
  void SERVICE_UNAVAILABLE_재시도_후_성공() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "연결 실패"))
        .doThrow(new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "연결 실패"))
        .doReturn(response)
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    GlucosePredictResponse result = client.predict(request);

    assertThat(result).isEqualTo(response);
    verify(client, times(GlucosePredictClientImpl.MAX_ATTEMPTS))
        .doPredict(any(), anyString(), anyInt());
  }

  @Test
  void 모든_시도_실패_시_마지막_예외_throw() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.TIMEOUT, "타임아웃"))
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    assertThatThrownBy(() -> client.predict(request))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.TIMEOUT);
    verify(client, times(GlucosePredictClientImpl.MAX_ATTEMPTS))
        .doPredict(any(), anyString(), anyInt());
  }

  @Test
  void INVALID_INPUT은_즉시_throw_재시도_없음() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.INVALID_INPUT, "잘못된 입력"))
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    assertThatThrownBy(() -> client.predict(request))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.INVALID_INPUT);
    verify(client, times(1)).doPredict(any(), anyString(), anyInt());
  }

  @Test
  void MODEL_ERROR는_즉시_throw_재시도_없음() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.MODEL_ERROR, "모델 오류"))
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    assertThatThrownBy(() -> client.predict(request))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.MODEL_ERROR);
    verify(client, times(1)).doPredict(any(), anyString(), anyInt());
  }

  @Test
  void MDC에_correlationId_있으면_그대로_사용하고_호출_종료_후에도_보존() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doReturn(response).when(client).doPredict(any(), anyString(), anyInt());

    String existingId = "test-correlation-12345";
    MDC.put(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY, existingId);
    try {
      client.predict(request);

      verify(client).doPredict(any(), eq(existingId), eq(1));
      // 외부에서 주입된 값은 호출 종료 후에도 그대로 — 클라이언트가 외부 컨텍스트를 덮어쓰지 않는다.
      assertThat(MDC.get(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY)).isEqualTo(existingId);
    } finally {
      MDC.remove(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY);
    }
  }

  @Test
  void MDC가_비어있던_상태면_호출_종료_시_정리됨() {
    // 회귀 방지: 이전 구현은 새 UUID 를 put 만 하고 remove 하지 않아 Tomcat 스레드 풀 재사용 시 다음 요청에 잔류값이 묻었음.
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doReturn(response).when(client).doPredict(any(), anyString(), anyInt());

    client.predict(request);

    verify(client).doPredict(any(), anyString(), eq(1));
    assertThat(MDC.get(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY)).isNull();
  }

  // ───────────────────────────────────────────────────────────────
  // mapRestClientException — body 파싱 단계에서 발생한 RestClientException 매핑.
  // ───────────────────────────────────────────────────────────────

  @Test
  void mapRestClientException_SocketTimeout_root는_TIMEOUT() {
    RestClientException e =
        new RestClientException(
            "Error while extracting response", new SocketTimeoutException("read"));

    AiServiceException result = helperClient.mapRestClientException(e, "corr", 1, 100);

    assertThat(result.getErrorType()).isEqualTo(ErrorType.TIMEOUT);
  }

  @Test
  void mapRestClientException_IOException_root는_SERVICE_UNAVAILABLE() {
    RestClientException e =
        new RestClientException("Error while extracting response", new IOException("disconnect"));

    AiServiceException result = helperClient.mapRestClientException(e, "corr", 1, 100);

    assertThat(result.getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
  }

  @Test
  void mapRestClientException_기타_root는_MODEL_ERROR() {
    RestClientException e = new RestClientException("Unexpected processing error");

    AiServiceException result = helperClient.mapRestClientException(e, "corr", 1, 100);

    assertThat(result.getErrorType()).isEqualTo(ErrorType.MODEL_ERROR);
  }

  @Test
  void mapRestClientException_중첩된_cause도_root까지_파헤침() {
    RestClientException e =
        new RestClientException(
            "outer", new RuntimeException("middle wrap", new SocketTimeoutException("read")));

    AiServiceException result = helperClient.mapRestClientException(e, "corr", 1, 100);

    assertThat(result.getErrorType()).isEqualTo(ErrorType.TIMEOUT);
  }

  @Test
  void 예외_경로에서도_MDC_정리됨() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.MODEL_ERROR, "모델 오류"))
        .when(client)
        .doPredict(any(), anyString(), anyInt());

    assertThatThrownBy(() -> client.predict(request)).isInstanceOf(AiServiceException.class);
    assertThat(MDC.get(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY)).isNull();
  }
}
