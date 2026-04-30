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

  private static final int PAGE_SIZE = 20;
  private static final String SUCCESS_CODE = "INFO-000";

  private final RestClient foodRestClient;
  private final FoodApiProperties properties;

  @Override
  public List<FoodApiItem> search(String query) {
    String uri =
        String.format("/%s/I2790/json/1/%d/DESC_KOR=%s", properties.serviceKey(), PAGE_SIZE, query);
    long startMs = System.currentTimeMillis();

    try {
      FoodApiResponse response =
          foodRestClient.get().uri(uri).retrieve().body(FoodApiResponse.class);

      long elapsedMs = System.currentTimeMillis() - startMs;
      log.info("[식품안전처API] 검색 완료 query={} elapsedMs={}", query, elapsedMs);

      if (response == null || response.i2790() == null) {
        return Collections.emptyList();
      }

      FoodApiResponse.Result result = response.i2790().result();
      if (result != null && !SUCCESS_CODE.equals(result.code())) {
        log.warn("[식품안전처API] 비정상 응답 code={} msg={}", result.code(), result.msg());
        return Collections.emptyList();
      }

      List<FoodApiItem> rows = response.i2790().rows();
      return rows != null ? rows : Collections.emptyList();

    } catch (ResourceAccessException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      log.warn("[식품안전처API] 연결 실패 query={} elapsedMs={}: {}", query, elapsedMs, e.getMessage());
      throw new FoodApiException("식품안전처 API 연결 실패", e);

    } catch (RestClientResponseException e) {
      long elapsedMs = System.currentTimeMillis() - startMs;
      log.warn(
          "[식품안전처API] HTTP 오류 query={} status={} elapsedMs={}",
          query,
          e.getStatusCode().value(),
          elapsedMs);
      throw new FoodApiException("식품안전처 API 오류 (HTTP " + e.getStatusCode().value() + ")", e);
    }
  }
}
