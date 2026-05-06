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
  private static final BigDecimal DEFAULT_SERVING_SIZE = new BigDecimal("100");

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
      // 탄수화물 누락/0 음식은 ingest 단계에서 차단. 혈당 예측 모델의 핵심 입력이라
      // 결측 시 비교 시뮬레이션이 무의미한 결과를 낸다.
      List<Food> saved = items.stream().filter(this::hasValidCarbs).map(this::upsert).toList();
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

  private boolean hasValidCarbs(FoodApiItem item) {
    BigDecimal carbs = parseBigDecimal(item.carbsG());
    return carbs != null && carbs.signum() > 0;
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
