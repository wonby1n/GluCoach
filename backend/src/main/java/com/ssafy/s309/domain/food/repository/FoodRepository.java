package com.ssafy.s309.domain.food.repository;

import com.ssafy.s309.domain.agent.dto.AgentUnseenFoodItem;
import com.ssafy.s309.domain.food.dto.KeyboardFoodItem;
import com.ssafy.s309.domain.food.entity.Food;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
      SELECT new com.ssafy.s309.domain.food.dto.KeyboardFoodItem(f.name, f.category, ufg.grade)
      FROM Food f
      LEFT JOIN UserFoodGrade ufg ON ufg.foodId = f.id AND ufg.userId = :userId
      ORDER BY (CASE WHEN ufg.grade IS NOT NULL THEN 0 ELSE 1 END), f.searchCount DESC, f.id ASC
      """)
  List<KeyboardFoodItem> findAllKeyboardFoods(@Param("userId") Integer userId);

  @Query(
      """
      SELECT new com.ssafy.s309.domain.agent.dto.AgentUnseenFoodItem(
          f.id, f.name, f.category, f.kcal, f.carbsG)
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
