package com.ssafy.s309.domain.meal.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.meal.dto.FoodGradeResponse;
import com.ssafy.s309.domain.meal.dto.MealRecordResponse;
import com.ssafy.s309.domain.meal.service.MealRecordService;
import com.ssafy.s309.domain.meal.service.UserFoodGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "식사", description = "음식 성적표 API")
@RestController
@RequestMapping("/api/food-grades")
@RequiredArgsConstructor
public class FoodGradeController {

  private final UserFoodGradeService userFoodGradeService;
  private final MealRecordService mealRecordService;

  @Operation(summary = "음식 성적표 조회", description = "사용자의 음식별 혈당 반응 등급 목록을 최근 섭취순으로 반환한다.")
  @GetMapping
  public ResponseEntity<List<FoodGradeResponse>> getMyFoodGrades(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(userFoodGradeService.getMyFoodGrades(principal.userId()));
  }

  @Operation(summary = "특정 음식 식사 기록 조회", description = "해당 음식을 먹은 식사 기록 목록을 최신순으로 반환한다.")
  @GetMapping("/{foodId}/meals")
  public ResponseEntity<List<MealRecordResponse>> getMealsByFood(
      @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Integer foodId) {
    return ResponseEntity.ok(mealRecordService.getByFoodId(principal.userId(), foodId));
  }
}
