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
  private static final BigDecimal DEFAULT_SERVING_SIZE = new BigDecimal("100");

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

    log.debug("[FoodSearch] 캐시 미스 — 식약처 API 호출 query={}", query);
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
    BigDecimal servingSize = parseServingSize(item.servingSize());
    if (servingSize == null) {
      servingSize = DEFAULT_SERVING_SIZE;
    }
    BigDecimal finalServingSize = servingSize;
    return foodRepository
        .findByFoodApiId(item.foodCd())
        .map(
            existing -> {
              existing.refresh(
                  item.categoryNm(),
                  parseBigDecimal(item.kcal()),
                  parseBigDecimal(item.carbsG()),
                  parseBigDecimal(item.sugarG()),
                  parseBigDecimal(item.proteinG()),
                  parseBigDecimal(item.fatG()),
                  parseBigDecimal(item.fiberG()),
                  parseBigDecimal(item.saturatedFatG()),
                  parseBigDecimal(item.transFatG()),
                  parseBigDecimal(item.cholesterolMg()),
                  parseBigDecimal(item.sodiumMg()));
              return existing;
            })
        .orElseGet(
            () ->
                foodRepository.save(
                    Food.builder()
                        .foodApiId(item.foodCd())
                        .name(item.foodNm())
                        .category(item.categoryNm())
                        .kcal(parseBigDecimal(item.kcal()))
                        .carbsG(parseBigDecimal(item.carbsG()))
                        .sugarG(parseBigDecimal(item.sugarG()))
                        .proteinG(parseBigDecimal(item.proteinG()))
                        .fatG(parseBigDecimal(item.fatG()))
                        .fiberG(parseBigDecimal(item.fiberG()))
                        .saturatedFatG(parseBigDecimal(item.saturatedFatG()))
                        .transFatG(parseBigDecimal(item.transFatG()))
                        .cholesterolMg(parseBigDecimal(item.cholesterolMg()))
                        .sodiumMg(parseBigDecimal(item.sodiumMg()))
                        .servingSize(finalServingSize)
                        .isCustomized(false)
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

  private BigDecimal parseServingSize(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String numeric = raw.replaceAll("[^0-9.]", "");
    if (numeric.isBlank()) return null;
    try {
      return new BigDecimal(numeric);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
