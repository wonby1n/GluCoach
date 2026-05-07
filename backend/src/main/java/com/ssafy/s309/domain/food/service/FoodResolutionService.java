package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CV 인식으로 받은 음식명을 foods 테이블 row 로 해석.
 *
 * <p>흐름: DB 캐시(영양정보 있는 row) → 식약처 API → customized 폴백. customized 분기는 carbsG 가 NULL 이라
 * 호출자(orchestrator)가 PENDING_NUTRITION 처리해야 한다.
 *
 * <p>TTL 무시 정책: 음식 영양정보는 거의 변하지 않으므로 cached_at 만료 여부와 무관하게 영양정보가 있는 가장 오래된 row 도 재사용한다. search() 와는
 * 다른 정책 (search 는 30일 TTL 적용해서 신선한 것만 표시).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodResolutionService {

  /** TTL 무시. {@code cached_at NOT NULL} 제약을 피하기 위한 가짜 하한. */
  private static final LocalDateTime EPOCH_THRESHOLD = LocalDateTime.of(2000, 1, 1, 0, 0);

  private final FoodRepository foodRepository;
  private final FoodApiClient foodApiClient;
  private final FoodService foodService;

  @Transactional
  public FoodResolution resolve(String name) {
    String normalized = normalize(name);

    Optional<Food> cacheHit = findCachedWithNutrition(normalized);
    if (cacheHit.isPresent()) {
      Food food = cacheHit.get();
      food.incrementSearchCount();
      log.info("[FoodResolution] CACHE_HIT name={} foodId={}", normalized, food.getId());
      return new FoodResolution(food, ResolutionStatus.CACHE_HIT);
    }

    List<Food> remote = fetchFromApi(normalized);
    if (!remote.isEmpty()) {
      Food food = remote.get(0);
      food.incrementSearchCount();
      log.info("[FoodResolution] REMOTE_FETCHED name={} foodId={}", normalized, food.getId());
      return new FoodResolution(food, ResolutionStatus.REMOTE_FETCHED);
    }

    Food customized = saveCustomized(normalized);
    log.info(
        "[FoodResolution] PENDING_NUTRITION name={} foodId={}", normalized, customized.getId());
    return new FoodResolution(customized, ResolutionStatus.PENDING_NUTRITION);
  }

  private String normalize(String name) {
    if (name == null) {
      throw new IllegalArgumentException("음식명이 null 입니다");
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("음식명이 비어있습니다");
    }
    return trimmed;
  }

  private Optional<Food> findCachedWithNutrition(String name) {
    return foodRepository
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            name, EPOCH_THRESHOLD)
        .stream()
        .filter(f -> f.getCarbsG() != null)
        .findFirst();
  }

  private List<Food> fetchFromApi(String name) {
    try {
      List<FoodApiItem> items = foodApiClient.search(name);
      return foodService.upsertFromApi(items);
    } catch (FoodApiException e) {
      log.warn(
          "[FoodResolution] 식약처 API 실패 → customized 폴백 name={} reason={}", name, e.getMessage());
      return List.of();
    }
  }

  private Food saveCustomized(String name) {
    return foodRepository.save(
        Food.builder()
            .name(name)
            .isCustomized(true)
            .searchCount(1)
            .cachedAt(LocalDateTime.now())
            .build());
  }
}
