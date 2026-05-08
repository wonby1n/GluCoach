package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 음식 검색 (DB 캐시 → 식약처 API → fallback).
 *
 * <p>트랜잭션 분리: 외부 HTTP 호출(식약처 API)을 트랜잭션 밖에서 수행하기 위해 본 클래스 자체는 {@code @Transactional} 을 갖지 않는다. DB
 * 조회/쓰기는 {@link FoodSearchTxHelper} 의 짧은 tx 메서드들로 분리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodService {

  static final int CACHE_TTL_DAYS = 30;

  private final FoodApiClient foodApiClient;
  private final FoodSearchTxHelper tx;

  public List<FoodSearchResult> search(String query) {
    String normalized = query.trim();
    long startMs = System.currentTimeMillis();

    List<FoodSearchResult> cached = tx.tryFreshCache(normalized);
    if (!cached.isEmpty()) {
      log.info(
          "[FoodSearch] cache=HIT query={} count={} elapsedMs={}",
          normalized,
          cached.size(),
          System.currentTimeMillis() - startMs);
      return cached;
    }

    try {
      List<FoodApiItem> items = foodApiClient.search(normalized);
      List<FoodSearchResult> saved = tx.upsertAndReturn(items);
      log.info(
          "[FoodSearch] cache=MISS query={} count={} elapsedMs={}",
          normalized,
          saved.size(),
          System.currentTimeMillis() - startMs);
      return saved;
    } catch (FoodApiException e) {
      List<FoodSearchResult> stale = tx.fallbackToStale(normalized);
      log.warn(
          "[FoodSearch] cache=FALLBACK query={} count={} elapsedMs={} reason={}",
          normalized,
          stale.size(),
          System.currentTimeMillis() - startMs,
          e.getMessage());
      return stale;
    }
  }
}
