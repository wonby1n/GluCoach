package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentFoodGradeItem;
import com.ssafy.s309.domain.agent.dto.AgentGlucoseRecentItem;
import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.agent.dto.AgentUserProfileItem;
import com.ssafy.s309.domain.agent.service.AgentUserDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 음식 추천 agent가 호출하는 user-scope read API (X-Agent-Api-Key 인증). */
@RestController
@RequestMapping("/api/agent/users/{userId}")
@RequiredArgsConstructor
@Tag(name = "AgentUserData", description = "AI Agent 전용: 사용자별 음식추천 데이터 (X-Agent-Api-Key 인증)")
public class AgentUserDataController {

  private final AgentUserDataService service;

  @Operation(summary = "사용자 음식 등급 (S/A/B/C/D) 조회")
  @GetMapping("/food-grades")
  public ResponseEntity<List<AgentFoodGradeItem>> foodGrades(@PathVariable Integer userId) {
    return ResponseEntity.ok(service.getFoodGrades(userId));
  }

  @Operation(summary = "사용자 프로필 (당뇨 타입/타겟 범위 등)")
  @GetMapping("/profile")
  public ResponseEntity<AgentUserProfileItem> profile(@PathVariable Integer userId) {
    return ResponseEntity.ok(service.getProfile(userId));
  }

  @Operation(summary = "최근 N일 식사 기록 (1~14)")
  @GetMapping("/recent-meals")
  public ResponseEntity<List<AgentMealItem>> recentMeals(
      @PathVariable Integer userId,
      @Parameter(description = "조회 일수 (1~14)", example = "2")
          @RequestParam(value = "days", defaultValue = "2")
          int days) {
    return ResponseEntity.ok(service.getRecentMeals(userId, days));
  }

  @Operation(summary = "최근 혈당 + 마지막 식사 경과 분")
  @GetMapping("/glucose-recent")
  public ResponseEntity<AgentGlucoseRecentItem> glucoseRecent(@PathVariable Integer userId) {
    return ResponseEntity.ok(service.getGlucoseRecent(userId));
  }
}
