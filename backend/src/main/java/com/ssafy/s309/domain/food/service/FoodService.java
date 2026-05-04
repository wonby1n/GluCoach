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

  static final int CACHE_TTL_DAYS = 30;
  private static final LocalDateTime EPOCH_THRESHOLD = LocalDateTime.of(2000, 1, 1, 0, 0);

  private final FoodRepository foodRepository;
  private final FoodApiClient foodApiClient;

  @Transactional
  public List<FoodSearchResult> search(String query) {
    String normalized = query.trim();
    long startMs = System.currentTimeMillis();
    LocalDateTime cacheThreshold = LocalDateTime.now().minusDays(CACHE_TTL_DAYS);

    List<Food> cached =
        foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            normalized, cacheThreshold);

    if (!cached.isEmpty()) {
      cached.forEach(Food::incrementSearchCount);
      log.info(
          "[FoodSearch] cache=HIT query={} count={} elapsedMs={}",
          normalized,
          cached.size(),
          System.currentTimeMillis() - startMs);
      return cached.stream().map(FoodSearchResult::from).toList();
    }

    try {
      List<FoodApiItem> items = foodApiClient.search(normalized);
      List<Food> saved = items.stream().map(this::upsert).toList();
      log.info(
          "[FoodSearch] cache=MISS query={} count={} elapsedMs={}",
          normalized,
          saved.size(),
          System.currentTimeMillis() - startMs);
      return saved.stream().map(FoodSearchResult::from).toList();
    } catch (FoodApiException e) {
      List<Food> stale =
          foodRepository.findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
              normalized, EPOCH_THRESHOLD);
      log.warn(
          "[FoodSearch] cache=FALLBACK query={} count={} elapsedMs={} reason={}",
          normalized,
          stale.size(),
          System.currentTimeMillis() - startMs,
          e.getMessage());
      return stale.stream().map(FoodSearchResult::from).toList();
    }
  }

  private Food upsert(FoodApiItem item) {
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
