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

  @Test
  void search_는_띄어쓰기만_다른_중복_row_를_제거하고_search_count_상위_row_만_유지한다() {
    // tryFreshCache 결과는 이미 search_count desc 정렬되어 있다고 가정.
    given(tx.tryFreshCache("샤브샤브"))
        .willReturn(List.of(result(1, "소고기 샤브샤브"), result(2, "소고기샤브샤브"), result(3, "갈비탕")));

    List<FoodSearchResult> out = foodService.search("샤브샤브");

    // 정규화 키 같은 두 row 중 첫 번째(id=1)만 유지, 다른 키(id=3)는 그대로.
    assertThat(out).extracting(FoodSearchResult::id).containsExactly(1, 3);
  }

  @Test
  void search_는_언더스코어_하이픈_변형도_중복으로_간주한다() {
    given(tx.tryFreshCache("김치찌개"))
        .willReturn(List.of(result(10, "김치찌개"), result(11, "김치_찌개"), result(12, "김치-찌개")));

    List<FoodSearchResult> out = foodService.search("김치찌개");

    assertThat(out).extracting(FoodSearchResult::id).containsExactly(10);
  }

  @Test
  void search_는_정규화_후_키가_다르면_모두_유지한다() {
    // "김치찌개", "김치찌개_햄", "햄_김치찌개" 는 정규화 후 키가 모두 달라 dedup 안 됨.
    given(tx.tryFreshCache("김치찌개"))
        .willReturn(List.of(result(20, "김치찌개"), result(21, "김치찌개_햄"), result(22, "햄_김치찌개")));

    List<FoodSearchResult> out = foodService.search("김치찌개");

    assertThat(out).extracting(FoodSearchResult::id).containsExactly(20, 21, 22);
  }
}
