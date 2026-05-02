package com.ssafy.s309.domain.food.client;

import com.ssafy.s309.config.FoodApiProperties;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.client.dto.FoodApiResponse;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FoodApiClientImpl implements FoodApiClient {

  private static final String SEARCH_PATH = "/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02";
  private static final int PAGE_SIZE = 20;
  private static final String SUCCESS_CODE = "00";

  private final RestClient foodRestClient;
  private final FoodApiProperties properties;

  @Override
  public List<FoodApiItem> search(String query) {
    long startMs = System.currentTimeMillis();

    try {
      FoodApiResponse response =
          foodRestClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(SEARCH_PATH)
                          .queryParam("serviceKey", properties.serviceKey())
                          .queryParam("type", "json")
                          .queryParam("pageNo", 1)
                          .queryParam("numOfRows", PAGE_SIZE)
                          .queryParam("foodNm", query)
                          .build())
              .retrieve()
              .body(FoodApiResponse.class);

      long elapsedMs = System.currentTimeMillis() - startMs;
      log.info("[식약처API] 검색 완료 query={} elapsedMs={}", query, elapsedMs);

      if (response == null || response.response() == null) {
        return Collections.emptyList();
      }

      FoodApiResponse.Header header = response.response().header();
      if (header != null && !SUCCESS_CODE.equals(header.resultCode())) {
        log.warn("[식약처API] 비정상 응답 code={} msg={}", header.resultCode(), header.resultMsg());
        return Collections.emptyList();
      }

      FoodApiResponse.Body body = response.response().body();
      if (body == null || body.items() == null) {
        return Collections.emptyList();
      }
      return body.items();

    } catch (ResourceAccessException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      log.warn("[식약처API] 연결 실패 query={} elapsedMs={}: {}", query, elapsedMs, e.getMessage());
      throw new FoodApiException("식약처 API 연결 실패", e);

    } catch (RestClientResponseException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      log.warn(
          "[식약처API] HTTP 오류 query={} status={} elapsedMs={}",
          query,
          e.getStatusCode().value(),
          elapsedMs);
      throw new FoodApiException("식약처 API 오류 (HTTP " + e.getStatusCode().value() + ")", e);
    }
  }
}
