package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식약처 API 결과를 foods 테이블에 upsert 하는 공유 helper.
 *
 * <p>FoodService.search / FoodResolutionService.resolve 양쪽이 동일 upsert 로직을 사용하므로 추출. 또한 두 호출자가 모두 tx
 * 분리 패턴을 쓰면서 자기 helper 가 본 helper 를 의존하게 만드는 cycle 을 끊는 역할도 함.
 *
 * <p>탄수화물 결측 항목은 차단 — 혈당 예측 모델의 핵심 입력이라 NULL 이면 비교 시뮬레이션이 무의미해진다.
 */
@Component
@RequiredArgsConstructor
class FoodApiUpsertHelper {

  private static final BigDecimal DEFAULT_SERVING_SIZE = new BigDecimal("100");

  private final FoodRepository foodRepository;

  @Transactional
  public List<Food> upsertFromApi(List<FoodApiItem> items) {
    return items.stream().filter(this::hasValidCarbs).map(this::upsert).toList();
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
