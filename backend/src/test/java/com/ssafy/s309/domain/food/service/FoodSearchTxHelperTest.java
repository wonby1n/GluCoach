package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodSearchTxHelperTest {

  @Mock private FoodRepository foodRepository;
  @Mock private FoodApiUpsertHelper upsertHelper;
  @InjectMocks private FoodSearchTxHelper tx;

  private static Food food(Integer id, String name, int searchCount, LocalDateTime cachedAt) {
    Food f =
        Food.builder()
            .name(name)
            .kcal(new BigDecimal("143.00"))
            .carbsG(new BigDecimal("31.50"))
            .searchCount(searchCount)
            .cachedAt(cachedAt)
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void tryFreshCache_30일_TTL_threshold_적용() {
    given(
            foodRepository.findTop20ByNameContainingWithExactMatchFirst(
                anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());

    tx.tryFreshCache("사과");

    ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(foodRepository)
        .findTop20ByNameContainingWithExactMatchFirst(anyString(), thresholdCaptor.capture());
    LocalDateTime expected = LocalDateTime.now().minusDays(FoodService.CACHE_TTL_DAYS);
    assertThat(thresholdCaptor.getValue())
        .isBetween(expected.minusSeconds(5), expected.plusSeconds(5));
  }

  @Test
  void tryFreshCache_hit_시_searchCount_증가() {
    Food cached = food(1, "밥, 흰쌀", 5, LocalDateTime.now().minusDays(3));
    given(
            foodRepository.findTop20ByNameContainingWithExactMatchFirst(
                anyString(), any(LocalDateTime.class)))
        .willReturn(List.of(cached));

    List<FoodSearchResult> results = tx.tryFreshCache("밥");

    assertThat(results).hasSize(1);
    assertThat(cached.getSearchCount()).isEqualTo(6);
  }

  @Test
  void tryFreshCache_miss_시_빈_리스트() {
    given(
            foodRepository.findTop20ByNameContainingWithExactMatchFirst(
                anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());

    List<FoodSearchResult> results = tx.tryFreshCache("없는음식");

    assertThat(results).isEmpty();
  }

  @Test
  void upsertAndReturn_upsertHelper_위임() {
    Food saved = food(2, "현미밥", 0, LocalDateTime.now());
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of(saved));

    List<FoodSearchResult> results = tx.upsertAndReturn(List.of());

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("현미밥");
  }

  @Test
  void fallbackToStale_TTL_무시하고_조회() {
    Food stale = food(3, "잡곡밥", 1, LocalDateTime.now().minusDays(60));
    given(
            foodRepository.findTop20ByNameContainingWithExactMatchFirst(
                anyString(), any(LocalDateTime.class)))
        .willReturn(List.of(stale));

    List<FoodSearchResult> results = tx.fallbackToStale("잡곡밥");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).name()).isEqualTo("잡곡밥");
    // EPOCH 하한이라 cached_at 만료 여부 무관 — threshold 가 2000-01-01 인지 확인
    ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(foodRepository)
        .findTop20ByNameContainingWithExactMatchFirst(anyString(), captor.capture());
    assertThat(captor.getValue()).isEqualTo(LocalDateTime.of(2000, 1, 1, 0, 0));
  }
}
