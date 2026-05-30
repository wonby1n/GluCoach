package com.ssafy.s309.domain.food.repository;

import com.ssafy.s309.domain.agent.dto.AgentUnseenFoodItem;
import com.ssafy.s309.domain.food.dto.KeyboardFoodItem;
import com.ssafy.s309.domain.food.entity.Food;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodRepository extends JpaRepository<Food, Integer> {

  Optional<Food> findByFoodApiId(String foodApiId);

  /**
   * 부분 일치 검색 — name + displayName 양쪽 매칭. 정확 일치 row 를 최상단으로 고정한 뒤 그 안에서 search_count desc.
   *
   * <p>"라면" 입력 시 합성어("짬뽕라면", "라면_치즈" 등)가 인기 정렬로 앞서 나와 오매칭되는 것을 막기 위함. 자동완성/CV 라벨 해석 양쪽에서 공유.
   *
   * <p>V18 LLM 정제로 displayName 이 raw name 과 달라진 케이스(예: name="비빔밥_혼합곡류", displayName="비빔밥") 대비 —
   * 사용자가 화면에 보이는 displayName 으로 검색해도 매칭되도록 OR 절 + ORDER BY 에 displayName 정확 일치 우선순위 포함.
   */
  @Query(
      """
      SELECT f FROM Food f
      WHERE (LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%'))
          OR LOWER(f.displayName) LIKE LOWER(CONCAT('%', :name, '%')))
      AND f.cachedAt > :cachedAtAfter
      ORDER BY
        CASE
          WHEN LOWER(f.name) = LOWER(:name) THEN 0
          WHEN LOWER(f.displayName) = LOWER(:name) THEN 1
          ELSE 2
        END,
        f.searchCount DESC
      """)
  List<Food> findByNameContainingWithExactMatchFirst(
      @Param("name") String name,
      @Param("cachedAtAfter") LocalDateTime cachedAtAfter,
      Pageable pageable);

  default List<Food> findTop20ByNameContainingWithExactMatchFirst(
      String name, LocalDateTime cachedAtAfter) {
    return findByNameContainingWithExactMatchFirst(name, cachedAtAfter, PageRequest.of(0, 20));
  }

  /**
   * 정확 일치 (대소문자 무시) — CV 라벨 해석 시 부분일치보다 우선 매칭하기 위함.
   *
   * <p>예: "비빔밥" 입력 → "돼지비빔밥(sc=500)"이 search_count 정렬로 "비빔밥(sc=10)"보다 앞서는 오매칭 방지.
   */
  List<Food> findByNameIgnoreCaseOrderBySearchCountDesc(String name);

  /**
   * displayName 정확 일치 (대소문자 무시) — 식약처 raw name 과 사용자 노출명이 다른 경우 대비.
   *
   * <p>예: name="비빔밥_혼합곡류", displayName="비빔밥". CV 가 "비빔밥"으로 인식했을 때 name 매칭 실패해도 displayName 으로 잡아 빈
   * customized row 누적 방지.
   */
  List<Food> findByDisplayNameIgnoreCaseOrderBySearchCountDesc(String displayName);

  /**
   * Agent 음식 추천용 — 사용자가 등급(user_food_grades) 또는 최근 식사(meal_records 7일)에 없는 foods 후보. search_count
   * desc 정렬, Pageable로 limit 제어.
   */
  /**
   * IME 키보드용 — 모든 foods + 사용자 등급 LEFT JOIN. 한 번에 다 내려서 IME가 메모리에 보유. 등급 있는 음식 먼저, 그 다음 search_count
   * desc.
   *
   * <p>현재 DB 규모 ~19,600개 → 단일 SELECT 약 50~150ms. 1일 1회 동기화라 부담 없음.
   */
  @Query(
      """
      SELECT new com.ssafy.s309.domain.food.dto.KeyboardFoodItem(f.name, f.displayName, f.category, ufg.grade)
      FROM Food f
      LEFT JOIN UserFoodGrade ufg ON ufg.foodId = f.id AND ufg.userId = :userId
      ORDER BY (CASE WHEN ufg.grade IS NOT NULL THEN 0 ELSE 1 END), f.searchCount DESC, f.id ASC
      """)
  List<KeyboardFoodItem> findAllKeyboardFoods(@Param("userId") Integer userId);

  @Query(
      """
      SELECT new com.ssafy.s309.domain.agent.dto.AgentUnseenFoodItem(
          f.id, f.name, f.displayName, f.category, f.kcal, f.carbsG)
      FROM Food f
      WHERE f.id NOT IN (
          SELECT ufg.foodId FROM UserFoodGrade ufg WHERE ufg.userId = :userId
      )
      AND f.id NOT IN (
          SELECT mr.foodId FROM MealRecord mr
          WHERE mr.userId = :userId AND mr.recordedAt > :sinceDate AND mr.foodId IS NOT NULL
      )
      ORDER BY f.searchCount DESC, f.id ASC
      """)
  List<AgentUnseenFoodItem> findUnseenForAgent(
      @Param("userId") Integer userId,
      @Param("sinceDate") LocalDateTime sinceDate,
      Pageable pageable);
}
