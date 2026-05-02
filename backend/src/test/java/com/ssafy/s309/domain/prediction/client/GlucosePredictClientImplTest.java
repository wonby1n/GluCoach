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

import com.ssafy.s309.domain.prediction.client.dto.FoodNutrition;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.UserProfile;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GlucosePredictClientImplTest {

  @Mock private RestClient restClient;

  private GlucosePredictRequest request;
  private GlucosePredictResponse response;

  @BeforeEach
  void setUp() {
    FoodNutrition food =
        new FoodNutrition(
            "1",
            "흰쌀밥",
            new BigDecimal("56.00"),
            new BigDecimal("4.40"),
            new BigDecimal("0.50"),
            new BigDecimal("250.00"),
            new BigDecimal("0.30"),
            86);
    UserProfile profile = new UserProfile("T2D", true, 175f, 70f, 105.0);
    request = new GlucosePredictRequest(food, profile, "generic");
    response = new GlucosePredictResponse(List.of(), 168.0, 45, 95, "generic", 0.82);
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
  void MDC에_correlationId_있으면_그대로_사용() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doReturn(response).when(client).doPredict(any(), anyString(), anyInt());

    String existingId = "test-correlation-12345";
    MDC.put(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY, existingId);
    try {
      client.predict(request);
      verify(client).doPredict(any(), eq(existingId), eq(1));
    } finally {
      MDC.remove(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY);
    }
  }

  @Test
  void MDC에_correlationId_없으면_새로_생성() {
    GlucosePredictClientImpl client = spy(new GlucosePredictClientImpl(restClient));
    doReturn(response).when(client).doPredict(any(), anyString(), anyInt());

    client.predict(request);

    verify(client).doPredict(any(), anyString(), eq(1));
    assertThat(MDC.get(GlucosePredictClientImpl.CORRELATION_ID_MDC_KEY)).isNotBlank();
  }
}
