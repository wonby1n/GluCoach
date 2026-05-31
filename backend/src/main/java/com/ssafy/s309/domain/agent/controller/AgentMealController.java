package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.agent.service.AgentMealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/meals")
@RequiredArgsConstructor
@Tag(name = "Agent API", description = "AI Agent 전용: 일별 식사 + 영양 정보 (X-Agent-Api-Key 인증)")
public class AgentMealController {

  private final AgentMealService service;

  @Operation(
      summary = "일별 식사 조회",
      description =
          "meal_records ⨝ foods로 (mealId, timestamp, foodName, carbs, protein, fat, calories) 반환. "
              + "date를 [date 00:00:00, date 23:59:59.999999999] 범위로 변환. recorded_at ASC 정렬.")
  @GetMapping
  public ResponseEntity<List<AgentMealItem>> getMeals(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId,
      @Parameter(description = "기준일 (YYYY-MM-DD)", example = "2026-05-03")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(service.getMeals(userId, date));
  }
}
