package com.ssafy.s309.domain.food.repository;

import com.ssafy.s309.domain.food.entity.Food;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Integer> {

  Optional<Food> findByFoodApiId(String foodApiId);

  List<Food> findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
      String name, LocalDateTime cachedAtAfter);

  /**
   * 정확 일치 (대소문자 무시) — CV 라벨 해석 시 부분일치보다 우선 매칭하기 위함.
   *
   * <p>예: "비빔밥" 입력 → "돼지비빔밥(sc=500)"이 search_count 정렬로 "비빔밥(sc=10)"보다 앞서는 오매칭 방지.
   */
  List<Food> findByNameIgnoreCaseOrderBySearchCountDesc(String name);
}
