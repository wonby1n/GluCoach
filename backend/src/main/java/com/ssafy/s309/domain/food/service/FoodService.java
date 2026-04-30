package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {

  private static final int CACHE_TTL_DAYS = 30;

  private final FoodRepository foodRepository;
  private final FoodApiClient foodApiClient;

  @Transactional
  public List<FoodSearchResult> search(String query) {
    LocalDateTime cacheThreshold = LocalDateTime.now().minusDays(CACHE_TTL_DAYS);
    List<Food> cached =
        foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            query, cacheThreshold);

    if (!cached.isEmpty()) {
      log.debug("[FoodSearch] 캐시 히트 query={} count={}", query, cached.size());
      cached.forEach(Food::incrementSearchCount);
      return cached.stream().map(FoodSearchResult::from).toList();
    }

    log.debug("[FoodSearch] 캐시 미스 — 식품안전처 API 호출 query={}", query);
    try {
      List<FoodApiItem> items = foodApiClient.search(query);
      List<Food> saved = items.stream().map(item -> upsert(item, cacheThreshold)).toList();
      return saved.stream().map(FoodSearchResult::from).toList();
    } catch (FoodApiException e) {
      log.warn("[FoodSearch] API 장애 — 만료 캐시 fallback query={}: {}", query, e.getMessage());
      List<Food> stale =
          foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
              query, LocalDateTime.of(2000, 1, 1, 0, 0));
      return stale.stream().map(FoodSearchResult::from).toList();
    }
  }

  private Food upsert(FoodApiItem item, LocalDateTime cacheThreshold) {
    return foodRepository
        .findByFoodApiId(item.foodCd())
        .map(
            existing -> {
              existing.refresh(
                  parseBigDecimal(item.kcal()),
                  parseBigDecimal(item.carbsG()),
                  parseBigDecimal(item.sugarG()),
                  parseBigDecimal(item.proteinG()),
                  parseBigDecimal(item.fatG()));
              return existing;
            })
        .orElseGet(
            () ->
                foodRepository.save(
                    Food.builder()
                        .foodApiId(item.foodCd())
                        .name(item.nameKor())
                        .kcal(parseBigDecimal(item.kcal()))
                        .carbsG(parseBigDecimal(item.carbsG()))
                        .sugarG(parseBigDecimal(item.sugarG()))
                        .proteinG(parseBigDecimal(item.proteinG()))
                        .fatG(parseBigDecimal(item.fatG()))
                        .searchCount(0)
                        .cachedAt(LocalDateTime.now())
                        .build()));
  }

  private BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
