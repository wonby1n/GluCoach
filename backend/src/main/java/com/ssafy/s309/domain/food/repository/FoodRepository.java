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
  /** IME 키보드용 — 사용자가 등급을 가진 음식 목록 (이름·카테고리·등급). */
  @Query(
      """
      SELECT new com.ssafy.s309.domain.food.dto.KeyboardFoodItem(f.name, f.category, ufg.grade)
      FROM Food f
      JOIN UserFoodGrade ufg ON f.id = ufg.foodId
      WHERE ufg.userId = :userId
      ORDER BY ufg.updatedAt DESC
      """)
  List<KeyboardFoodItem> findKeyboardGradesByUserId(@Param("userId") Integer userId);

  /** IME 키보드용 — search_count 상위 음식 (등급 없음 fallback). */
  List<Food> findTop200ByOrderBySearchCountDesc();

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
