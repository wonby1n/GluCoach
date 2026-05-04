package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodServiceTest {

  @Mock private FoodRepository foodRepository;
  @Mock private FoodApiClient foodApiClient;
  @InjectMocks private FoodService foodService;

  private static Food food(
      Integer id, String apiId, String name, int searchCount, LocalDateTime cachedAt) {
    Food f =
        Food.builder()
            .foodApiId(apiId)
            .name(name)
            .kcal(new BigDecimal("143.00"))
            .carbsG(new BigDecimal("31.50"))
            .sugarG(new BigDecimal("0.10"))
            .proteinG(new BigDecimal("2.60"))
            .fatG(new BigDecimal("0.30"))
            .searchCount(searchCount)
            .cachedAt(cachedAt)
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void 캐시_히트_시_API_호출_없이_결과_반환() {
    Food cached = food(1, "D000006", "밥, 흰쌀", 5, LocalDateTime.now().minusDays(3));
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of(cached));

    List<FoodSearchResult> results = foodService.search("밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("밥, 흰쌀");
    verify(foodApiClient, never()).search(anyString());
    assertThat(cached.getSearchCount()).isEqualTo(6);
  }

  @Test
  void 캐시_미스_시_API_호출_후_저장() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "현미밥", null, null, "150", "5.0", "1.0", "32.0", "0.2", null, null, null,
            null, null);
    given(foodApiClient.search("현미밥")).willReturn(List.of(apiItem));
    given(foodRepository.findByFoodApiId("D000007")).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    List<FoodSearchResult> results = foodService.search("현미밥");

    assertThat(results).hasSize(1);
    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    Food saved = captor.getValue();
    assertThat(saved.getFoodApiId()).isEqualTo("D000007");
    assertThat(saved.getName()).isEqualTo("현미밥");
    assertThat(saved.getKcal()).isEqualByComparingTo("150");
    assertThat(saved.getCarbsG()).isEqualByComparingTo("32.0");
  }

  @Test
  void 캐시_미스_기존_entity_있으면_refresh() {
    Food existing = food(2, "D000007", "현미밥", 10, LocalDateTime.now().minusDays(40));
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "현미밥", null, null, "155", "5.5", "1.1", "33.0", "0.3", null, null, null,
            null, null);
    given(foodApiClient.search("현미밥")).willReturn(List.of(apiItem));
    given(foodRepository.findByFoodApiId("D000007")).willReturn(Optional.of(existing));

    foodService.search("현미밥");

    verify(foodRepository, never()).save(any(Food.class));
    assertThat(existing.getKcal()).isEqualByComparingTo("155");
    assertThat(existing.getCachedAt())
        .asInstanceOf(InstanceOfAssertFactories.LOCAL_DATE_TIME)
        .isAfter(LocalDateTime.now().minusMinutes(1));
  }

  @Test
  void API_장애_시_만료된_캐시로_fallback() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of()) // 첫 호출(유효 캐시) — 미스
        .willReturn(
            List.of(food(3, "D000008", "잡곡밥", 1, LocalDateTime.now().minusDays(60)))); // fallback
    given(foodApiClient.search("잡곡밥")).willThrow(new FoodApiException("식품안전처 API 연결 실패"));

    List<FoodSearchResult> results = foodService.search("잡곡밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("잡곡밥");
    verify(foodRepository, times(2))
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            anyString(), any(LocalDateTime.class));
  }

  @Test
  void API_장애_fallback_캐시도_없으면_빈_결과() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search(anyString())).willThrow(new FoodApiException("식품안전처 API 연결 실패"));

    List<FoodSearchResult> results = foodService.search("없는음식");

    assertThat(results).isEmpty();
  }

  @Test
  void 쿼리_공백_trim_정규화() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search(anyString())).willReturn(List.of());

    foodService.search("  사과  ");

    verify(foodApiClient).search("사과");
  }

  @Test
  void 캐시_만료_threshold_30일_적용() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search(anyString())).willReturn(List.of());

    foodService.search("사과");

    ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(foodRepository)
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            anyString(), thresholdCaptor.capture());
    LocalDateTime expected = LocalDateTime.now().minusDays(FoodService.CACHE_TTL_DAYS);
    assertThat(thresholdCaptor.getValue())
        .isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
  }
}
