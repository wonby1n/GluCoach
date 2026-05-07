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
    Optional<Food> remoteMatch = pickBestRemoteMatch(remote, normalized);
    if (remoteMatch.isPresent()) {
      Food food = remoteMatch.get();
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

  /**
   * 영양정보 있는 캐시 row 검색.
   *
   * <p>① 정확 일치 우선 → ② 부분 일치 fallback. 정확 일치를 먼저 보지 않으면 "비빔밥" 입력 시 검색량 큰 "돼지비빔밥"이 잡혀 영양정보가 다른 음식
   * 데이터로 예측 모델 입력이 오염됨.
   *
   * <p>{@code is_customized=true} 라도 사용자가 직접 입력해 영양정보를 채운 row 라면 재사용한다 (foods.is_customized 컬럼
   * comment "음식 인식 실패 시 텍스트 입력" 의도와 일치).
   */
  private Optional<Food> findCachedWithNutrition(String name) {
    Optional<Food> exact =
        foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc(name).stream()
            .filter(f -> f.getCarbsG() != null)
            .findFirst();
    if (exact.isPresent()) {
      return exact;
    }
    return foodRepository
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            name, EPOCH_THRESHOLD)
        .stream()
        .filter(f -> f.getCarbsG() != null)
        .findFirst();
  }

  /**
   * 식약처 API 결과 중 입력 음식명과 의미적으로 가까운 row 선별.
   *
   * <p>식약처 API 의 fuzzy 매칭이 무관한 음식까지 결과에 포함시키는 경우가 있다 (예: "비빔밥" 검색 시 "국밥_돼지머리"가 결과에 섞여 들어옴). 첫 원소를
   * 무조건 채택하면 사용자에게 잘못된 음식 정보·예측 곡선이 제공돼 신뢰도 손상.
   *
   * <p>우선순위: ① 정확 일치 (case-insensitive) → ② input 이 candidate name 의 substring → 둘 다 없으면 빈 Optional
   * → 호출자가 PENDING_NUTRITION 으로 분기. "그래도 의미 있는 fuzzy 결과(예: 전주비빔밥)" 는 ② 에서 잡힘.
   */
  private Optional<Food> pickBestRemoteMatch(List<Food> candidates, String input) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    Optional<Food> exact =
        candidates.stream().filter(f -> f.getName().equalsIgnoreCase(input)).findFirst();
    if (exact.isPresent()) {
      return exact;
    }
    String inputLower = input.toLowerCase();
    return candidates.stream()
        .filter(f -> f.getName().toLowerCase().contains(inputLower))
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
