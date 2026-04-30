package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import java.net.SocketTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GlucosePredictClientImpl implements GlucosePredictClient {

  private static final String PREDICT_PATH = "/inference/glucose";

  private final RestClient aiRestClient;

  @Override
  public GlucosePredictResponse predict(GlucosePredictRequest request) {
    try {
      GlucosePredictResponse response =
          aiRestClient
              .post()
              .uri(PREDICT_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(GlucosePredictResponse.class);

      if (response == null) {
        throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스로부터 빈 응답을 받았습니다");
      }
      return response;

    } catch (ResourceAccessException e) {
      if (e.getCause() instanceof SocketTimeoutException) {
        throw new AiServiceException(ErrorType.TIMEOUT, "AI 서비스 응답 시간 초과", e);
      }
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다", e);

    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 400) {
        throw new AiServiceException(
            ErrorType.INVALID_INPUT, "AI 서비스 입력 데이터 오류: " + e.getMessage(), e);
      }
      throw new AiServiceException(
          ErrorType.MODEL_ERROR, "AI 서비스 오류 (HTTP " + e.getStatusCode().value() + ")", e);
    }
  }
}
