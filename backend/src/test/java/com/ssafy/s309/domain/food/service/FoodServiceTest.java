package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FoodService 의 search 오케스트레이션. 캐시 hit / API 호출 / 장애 fallback 의 분기 검증만. TTL/upsert/매핑 디테일은
 * FoodSearchTxHelper / FoodApiUpsertHelper 에서 별도 테스트.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodServiceTest {

  @Mock private FoodApiClient foodApiClient;
  @Mock private FoodSearchTxHelper tx;
  @InjectMocks private FoodService foodService;

  private static FoodSearchResult result(Integer id, String name) {
    return new FoodSearchResult(
        id,
        name,
        null,
        null,
        new BigDecimal("143.00"),
        new BigDecimal("31.50"),
        new BigDecimal("0.10"),
        new BigDecimal("2.60"),
        new BigDecimal("0.30"),
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  @Test
  void 캐시_히트_시_API_호출_없이_결과_반환() {
    given(tx.tryFreshCache("밥")).willReturn(List.of(result(1, "밥, 흰쌀")));

    List<FoodSearchResult> results = foodService.search("밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("밥, 흰쌀");
    verify(foodApiClient, never()).search(anyString());
    verify(tx, never()).upsertAndReturn(anyList());
    verify(tx, never()).fallbackToStale(anyString());
  }

  @Test
  void 캐시_미스_시_API_호출_후_upsert_위임() {
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "현미밥", null, null, "150", "5.0", "1.0", "32.0", "0.2", null, null, null,
            null, null);
    given(tx.tryFreshCache("현미밥")).willReturn(List.of());
    given(foodApiClient.search("현미밥")).willReturn(List.of(apiItem));
    given(tx.upsertAndReturn(List.of(apiItem))).willReturn(List.of(result(2, "현미밥")));

    List<FoodSearchResult> results = foodService.search("현미밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("현미밥");
    verify(tx, never()).fallbackToStale(anyString());
  }

  @Test
  void API_장애_시_helper_fallback_위임() {
    given(tx.tryFreshCache(anyString())).willReturn(List.of());
    given(foodApiClient.search(anyString())).willThrow(new FoodApiException("식품안전처 API 연결 실패"));
    given(tx.fallbackToStale("잡곡밥")).willReturn(List.of(result(3, "잡곡밥")));

    List<FoodSearchResult> results = foodService.search("잡곡밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("잡곡밥");
  }

  @Test
  void API_장애_fallback_캐시도_없으면_빈_결과() {
    given(tx.tryFreshCache(anyString())).willReturn(List.of());
    given(foodApiClient.search(anyString())).willThrow(new FoodApiException("식품안전처 API 연결 실패"));
    given(tx.fallbackToStale(anyString())).willReturn(List.of());

    List<FoodSearchResult> results = foodService.search("없는음식");

    assertThat(results).isEmpty();
  }

  @Test
  void 쿼리_공백_trim_정규화() {
    given(tx.tryFreshCache("사과")).willReturn(List.of());
    given(foodApiClient.search("사과")).willReturn(List.of());
    given(tx.upsertAndReturn(List.of())).willReturn(List.of());

    foodService.search("  사과  ");

    verify(foodApiClient).search("사과");
  }
}
