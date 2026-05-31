package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
      List<FoodSearchResult> deduped = dedupByNormalizedName(cached);
      log.info(
          "[FoodSearch] cache=HIT query={} count={} elapsedMs={}",
          normalized,
          deduped.size(),
          System.currentTimeMillis() - startMs);
      return deduped;
    }

    try {
      List<FoodApiItem> items = foodApiClient.search(normalized);
      List<FoodSearchResult> deduped = dedupByNormalizedName(tx.upsertAndReturn(items));
      log.info(
          "[FoodSearch] cache=MISS query={} count={} elapsedMs={}",
          normalized,
          deduped.size(),
          System.currentTimeMillis() - startMs);
      return deduped;
    } catch (FoodApiException e) {
      List<FoodSearchResult> deduped = dedupByNormalizedName(tx.fallbackToStale(normalized));
      log.warn(
          "[FoodSearch] cache=FALLBACK query={} count={} elapsedMs={} reason={}",
          normalized,
          deduped.size(),
          System.currentTimeMillis() - startMs,
          e.getMessage());
      return deduped;
    }
  }

  /**
   * 사용자 노출 기준 중복 제거.
   *
   * <p>키 선택 정책:
   *
   * <ul>
   *   <li>display_name 이 있으면 그것의 공백 제거값을 키로 — V18 LLM 정제 결과가 같으면 같은 음식으로 간주. raw name 이 달라도 사용자에겐
   *       동일 표시명이라 한 줄로 통합 (예: raw "국밥_돼지머리" + raw "돼지머리국밥" → 둘 다 display "돼지머리국밥").
   *   <li>display_name 이 없으면 raw name 의 공백/언더스코어/하이픈을 제거한 값으로 fallback — 띄어쓰기/표기 차이만 있는 row 통합 (예:
   *       "소고기 샤브샤브" vs "소고기샤브샤브").
   * </ul>
   *
   * <p>입력은 이미 search_count desc 정렬 상태이므로 LinkedHashMap putIfAbsent 로 첫 번째(가장 인기) row 만 유지된다.
   */
  private static List<FoodSearchResult> dedupByNormalizedName(List<FoodSearchResult> results) {
    LinkedHashMap<String, FoodSearchResult> seen = new LinkedHashMap<>();
    for (FoodSearchResult r : results) {
      String key =
          (r.displayName() != null && !r.displayName().isBlank())
              ? r.displayName().replaceAll("\\s", "")
              : r.name().replaceAll("[\\s_-]", "");
      seen.putIfAbsent(key, r);
    }
    return new ArrayList<>(seen.values());
  }
}
