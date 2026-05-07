package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FoodResolutionService 의 tx 경계 분리용 helper.
 *
 * <p>외부 HTTP 호출(식약처 API)을 트랜잭션 밖에서 수행하기 위해 DB 조회/쓰기 책임만 분리. 짧은 tx 로 격리해 외부 호출 응답 대기 동안 DB 커넥션 점유를
 * 막는다. {@code @Transactional} self-invocation 회피를 위해 별도 컴포넌트.
 */
@Component
@RequiredArgsConstructor
class FoodResolutionTxHelper {

  /** TTL 무시. {@code cached_at NOT NULL} 제약을 피하기 위한 가짜 하한. */
  private static final LocalDateTime EPOCH_THRESHOLD = LocalDateTime.of(2000, 1, 1, 0, 0);

  private final FoodRepository foodRepository;
  private final FoodApiUpsertHelper upsertHelper;

  /**
   * 영양정보 있는 캐시 row 검색 + 발견 시 searchCount 증가.
   *
   * <p>① 정확 일치 우선 → ② 부분 일치 fallback. {@code is_customized=true} 라도 사용자가 직접 입력해 영양정보를 채운 row 라면
   * 재사용.
   */
  @Transactional
  public Optional<Food> findAndMarkCacheHit(String name) {
    Optional<Food> hit = findCachedWithNutrition(name);
    hit.ifPresent(Food::incrementSearchCount);
    return hit;
  }

  /**
   * upsertFromApi 결과에서 best match 선택. 매칭 발견 시 REMOTE_FETCHED, 없으면 customized row 신규 저장 후
   * PENDING_NUTRITION.
   */
  @Transactional
  public FoodResolution resolveRemoteOrPending(List<FoodApiItem> items, String name) {
    List<Food> upserted = upsertHelper.upsertFromApi(items);
    Optional<Food> remoteMatch = pickBestRemoteMatch(upserted, name);
    if (remoteMatch.isPresent()) {
      Food food = remoteMatch.get();
      food.incrementSearchCount();
      return new FoodResolution(food, ResolutionStatus.REMOTE_FETCHED);
    }
    Food customized = saveCustomized(name);
    return new FoodResolution(customized, ResolutionStatus.PENDING_NUTRITION);
  }

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
   * 식약처 API 결과 중 입력 음식명과 의미적으로 가까운 row 선별. ① 정확 일치 (case-insensitive) → ② substring → 둘 다 없으면 빈
   * Optional.
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
