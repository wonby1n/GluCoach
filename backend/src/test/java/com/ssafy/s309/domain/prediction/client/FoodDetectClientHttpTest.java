package com.ssafy.s309.domain.prediction.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * RestClient 직호출 경로 검증 — 멀티파트 헤더, 상관관계ID 헤더, HTTP 응답코드별 ErrorType 매핑까지. 재시도/백오프 로직은 retry 단위 테스트가
 * 별도로 커버하므로 여기서는 doDetect()를 1회만 호출.
 */
@SuppressWarnings("NonAsciiCharacters")
class FoodDetectClientHttpTest {

  private static final String BASE_URL = "http://ai.test";
  private static final String DETECT_URL = BASE_URL + "/api/v1/food/detect";
  private static final String CORRELATION_ID = "test-corr-1131";
  private static final byte[] IMAGE_BYTES = new byte[] {1, 2, 3, 4};

  private MockRestServiceServer server;
  private FoodDetectClientImpl client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new FoodDetectClientImpl(builder.build());
  }

  @Test
  void 정상_응답_파싱_및_요청_헤더_검증() {
    String body =
        """
        {
          "count": 2,
          "detections": [
            {"name_ko": "비빔밥", "name_en": "bibimbap", "confidence": 0.91},
            {"name_ko": "김치",   "name_en": "kimchi",   "confidence": 0.65}
          ]
        }
        """;

    server
        .expect(requestTo(DETECT_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Correlation-Id", CORRELATION_ID))
        .andExpect(header("Content-Type", Matchers.startsWith("multipart/form-data")))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    FoodDetectResponse response =
        client.doDetect(IMAGE_BYTES, "bibimbap.jpg", "image/jpeg", CORRELATION_ID, 1);

    assertThat(response.count()).isEqualTo(2);
    assertThat(response.detections()).hasSize(2);
    assertThat(response.detections().get(0).nameKo()).isEqualTo("비빔밥");
    assertThat(response.detections().get(0).nameEn()).isEqualTo("bibimbap");
    assertThat(response.detections().get(0).confidence()).isEqualTo(0.91);
    assertThat(response.detections().get(1).nameEn()).isEqualTo("kimchi");
  }

  @Test
  void HTTP_400_INVALID_INPUT으로_매핑() {
    server.expect(requestTo(DETECT_URL)).andRespond(withBadRequest());

    assertThatThrownBy(
            () -> client.doDetect(IMAGE_BYTES, "test.jpg", "image/jpeg", CORRELATION_ID, 1))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.INVALID_INPUT);
  }

  @Test
  void HTTP_500_MODEL_ERROR로_매핑() {
    server.expect(requestTo(DETECT_URL)).andRespond(withServerError());

    assertThatThrownBy(
            () -> client.doDetect(IMAGE_BYTES, "test.jpg", "image/jpeg", CORRELATION_ID, 1))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.MODEL_ERROR);
  }

  @Test
  void SocketTimeout_TIMEOUT으로_매핑() {
    server
        .expect(requestTo(DETECT_URL))
        .andRespond(
            request -> {
              throw new SocketTimeoutException("read timeout");
            });

    assertThatThrownBy(
            () -> client.doDetect(IMAGE_BYTES, "test.jpg", "image/jpeg", CORRELATION_ID, 1))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.TIMEOUT);
  }

  @Test
  void 연결_실패_SERVICE_UNAVAILABLE로_매핑() {
    server
        .expect(requestTo(DETECT_URL))
        .andRespond(
            request -> {
              throw new ConnectException("connection refused");
            });

    assertThatThrownBy(
            () -> client.doDetect(IMAGE_BYTES, "test.jpg", "image/jpeg", CORRELATION_ID, 1))
        .isInstanceOf(AiServiceException.class)
        .extracting("errorType")
        .isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
  }
}
