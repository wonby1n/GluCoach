package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FoodService.search 의 tx 경계 분리용 helper.
 *
 * <p>식약처 API 호출을 트랜잭션 밖에서 수행하기 위해 DB 조회/쓰기 책임만 분리. 짧은 tx 로 격리해 외부 호출 응답 대기 동안 DB 커넥션 점유를 막는다.
 */
@Component
@RequiredArgsConstructor
class FoodSearchTxHelper {

  private static final LocalDateTime EPOCH_THRESHOLD = LocalDateTime.of(2000, 1, 1, 0, 0);

  private final FoodRepository foodRepository;
  private final FoodApiUpsertHelper upsertHelper;

  /** 30일 TTL 캐시 hit 체크 + searchCount 증가. hit 없으면 빈 List 반환. */
  @Transactional
  public List<FoodSearchResult> tryFreshCache(String query) {
    LocalDateTime threshold = LocalDateTime.now().minusDays(FoodService.CACHE_TTL_DAYS);
    List<Food> cached =
        foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            query, threshold);
    if (cached.isEmpty()) {
      return List.of();
    }
    cached.forEach(Food::incrementSearchCount);
    return cached.stream().map(FoodSearchResult::from).toList();
  }

  /** 식약처 API 결과 upsert + 응답 매핑. */
  @Transactional
  public List<FoodSearchResult> upsertAndReturn(List<FoodApiItem> items) {
    List<Food> saved = upsertHelper.upsertFromApi(items);
    return saved.stream().map(FoodSearchResult::from).toList();
  }

  /** API 장애 시 TTL 만료된 stale 캐시까지 포함해 fallback. */
  @Transactional(readOnly = true)
  public List<FoodSearchResult> fallbackToStale(String query) {
    List<Food> stale =
        foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            query, EPOCH_THRESHOLD);
    return stale.stream().map(FoodSearchResult::from).toList();
  }
}
