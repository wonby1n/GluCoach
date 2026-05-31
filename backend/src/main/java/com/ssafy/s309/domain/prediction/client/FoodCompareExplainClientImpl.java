package com.ssafy.s309.domain.prediction.client;

import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiRequest;
import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiResponse;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.exception.AiServiceException.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class FoodCompareExplainClientImpl implements FoodCompareExplainClient {

  private static final String EXPLAIN_PATH = "/agent/food-compare";

  private final RestClient aiExplainRestClient;

  public FoodCompareExplainClientImpl(
      @Qualifier("aiExplainRestClient") RestClient aiExplainRestClient) {
    this.aiExplainRestClient = aiExplainRestClient;
  }

  @Override
  public FoodCompareAiResponse explain(FoodCompareAiRequest request) {
    try {
      FoodCompareAiResponse response =
          aiExplainRestClient
              .post()
              .uri(EXPLAIN_PATH)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(FoodCompareAiResponse.class);

      if (response == null) {
        throw new AiServiceException(ErrorType.MODEL_ERROR, "AI 서비스로부터 빈 응답을 받았습니다");
      }
      return response;

    } catch (ResourceAccessException e) {
      log.warn("food-compare AI 호출 실패 (연결): {}", e.getMessage());
      throw new AiServiceException(ErrorType.SERVICE_UNAVAILABLE, "AI 서비스에 연결할 수 없습니다", e);
    } catch (RestClientResponseException e) {
      log.warn(
          "food-compare AI HTTP 오류 (status={}): {}", e.getStatusCode().value(), e.getMessage());
      throw new AiServiceException(
          ErrorType.MODEL_ERROR, "AI 비교 설명 오류 (HTTP " + e.getStatusCode().value() + ")", e);
    }
  }
}
