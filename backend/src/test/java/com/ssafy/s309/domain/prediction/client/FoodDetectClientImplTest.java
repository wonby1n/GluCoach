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

import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import com.ssafy.s309.domain.prediction.client.dto.FoodDetection;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodDetectClientImplTest {

  @Mock private RestClient restClient;

  private MultipartFile image;
  private FoodDetectResponse response;

  @BeforeEach
  void setUp() {
    image = new MockMultipartFile("file", "bibimbap.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
    response = new FoodDetectResponse(1, List.of(new FoodDetection("비빔밥", "bibimbap", 0.91)));
    MDC.remove(FoodDetectClientImpl.CORRELATION_ID_MDC_KEY);
  }

  @Test
  void 첫_시도_성공_시_재시도_없음() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    FoodDetectResponse result = client.detect(image);

    assertThat(result).isEqualTo(response);
    verify(client, times(1)).doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void 타임아웃_후_재시도_성공() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.TIMEOUT, "타임아웃"))
        .doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    FoodDetectResponse result = client.detect(image);

    assertThat(result).isEqualTo(response);
    verify(client, times(2)).doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void SERVICE_UNAVAILABLE_재시도_후_성공() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "연결 실패"))
        .doThrow(new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "연결 실패"))
        .doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    FoodDetectResponse result = client.detect(image);

    assertThat(result).isEqualTo(response);
    verify(client, times(FoodDetectClientImpl.MAX_ATTEMPTS))
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void 모든_시도_실패_시_마지막_예외_throw() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.TIMEOUT, "타임아웃"))
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    assertThatThrownBy(() -> client.detect(image))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.TIMEOUT);
    verify(client, times(FoodDetectClientImpl.MAX_ATTEMPTS))
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void INVALID_INPUT은_즉시_throw_재시도_없음() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.INVALID_INPUT, "잘못된 입력"))
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    assertThatThrownBy(() -> client.detect(image))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.INVALID_INPUT);
    verify(client, times(1)).doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void MODEL_ERROR는_즉시_throw_재시도_없음() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doThrow(new AiServiceException(ErrorType.MODEL_ERROR, "모델 오류"))
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    assertThatThrownBy(() -> client.detect(image))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.MODEL_ERROR);
    verify(client, times(1)).doDetect(any(), anyString(), anyString(), anyString(), anyInt());
  }

  @Test
  void MDC에_correlationId_있으면_그대로_사용() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    String existingId = "test-correlation-67890";
    MDC.put(FoodDetectClientImpl.CORRELATION_ID_MDC_KEY, existingId);
    try {
      client.detect(image);
      verify(client).doDetect(any(), anyString(), anyString(), eq(existingId), eq(1));
    } finally {
      MDC.remove(FoodDetectClientImpl.CORRELATION_ID_MDC_KEY);
    }
  }

  @Test
  void MDC에_correlationId_없으면_새로_생성() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    client.detect(image);

    verify(client).doDetect(any(), anyString(), anyString(), anyString(), eq(1));
    assertThat(MDC.get(FoodDetectClientImpl.CORRELATION_ID_MDC_KEY)).isNotBlank();
  }

  @Test
  void 파일_원본명_또는_컨텐츠타입_없으면_기본값_사용() {
    FoodDetectClientImpl client = spy(new FoodDetectClientImpl(restClient));
    doReturn(response)
        .when(client)
        .doDetect(any(), anyString(), anyString(), anyString(), anyInt());

    MultipartFile imageNoMeta = new MockMultipartFile("file", null, null, new byte[] {9, 9, 9});
    client.detect(imageNoMeta);

    verify(client).doDetect(any(), eq("image.jpg"), eq("image/jpeg"), anyString(), eq(1));
  }
}
