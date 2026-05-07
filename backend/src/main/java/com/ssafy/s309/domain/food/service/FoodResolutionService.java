package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CV 인식으로 받은 음식명을 foods 테이블 row 로 해석.
 *
 * <p>흐름: DB 캐시(영양정보 있는 row) → 식약처 API → customized 폴백. customized 분기는 carbsG 가 NULL 이라
 * 호출자(orchestrator)가 PENDING_NUTRITION 처리해야 한다.
 *
 * <p>트랜잭션 분리: 외부 HTTP 호출(식약처 API)을 트랜잭션 밖에서 수행하기 위해 본 클래스 자체는 {@code @Transactional} 을 갖지 않는다. DB
 * 조회/쓰기는 {@link FoodResolutionTxHelper} 의 짧은 tx 메서드들로 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodResolutionService {

  private final FoodApiClient foodApiClient;
  private final FoodResolutionTxHelper tx;

  public FoodResolution resolve(String name) {
    String normalized = normalize(name);

    Optional<Food> cacheHit = tx.findAndMarkCacheHit(normalized);
    if (cacheHit.isPresent()) {
      Food food = cacheHit.get();
      log.info("[FoodResolution] CACHE_HIT name={} foodId={}", normalized, food.getId());
      return new FoodResolution(food, ResolutionStatus.CACHE_HIT);
    }

    List<FoodApiItem> items = fetchFromApiNoTx(normalized);
    FoodResolution result = tx.resolveRemoteOrPending(items, normalized);
    log.info(
        "[FoodResolution] {} name={} foodId={}",
        result.status(),
        normalized,
        result.food().getId());
    return result;
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

  /** 외부 API 호출 — 트랜잭션 밖에서 실행되도록 본 서비스에 직접 두고 helper 호출 사이에 끼움. */
  private List<FoodApiItem> fetchFromApiNoTx(String name) {
    try {
      return foodApiClient.search(name);
    } catch (FoodApiException e) {
      log.warn(
          "[FoodResolution] 식약처 API 실패 → customized 폴백 name={} reason={}", name, e.getMessage());
      return List.of();
    }
  }
}
