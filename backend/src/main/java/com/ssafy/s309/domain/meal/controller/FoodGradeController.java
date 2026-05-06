package com.ssafy.s309.domain.meal.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.meal.dto.FoodGradeResponse;
import com.ssafy.s309.domain.meal.service.UserFoodGradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FoodGrade", description = "음식 성적표 API")
@RestController
@RequestMapping("/api/food-grades")
@RequiredArgsConstructor
public class FoodGradeController {

  private final UserFoodGradeService userFoodGradeService;

  @Operation(summary = "음식 성적표 조회", description = "사용자의 음식별 혈당 반응 등급 목록을 최근 섭취순으로 반환한다.")
  @GetMapping
  public ResponseEntity<List<FoodGradeResponse>> getMyFoodGrades(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(userFoodGradeService.getMyFoodGrades(principal.userId()));
  }
}
