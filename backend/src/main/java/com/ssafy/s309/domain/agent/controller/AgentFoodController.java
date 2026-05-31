package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentFoodPredictionResult;
import com.ssafy.s309.domain.agent.dto.AgentFoodSearchItem;
import com.ssafy.s309.domain.agent.service.AgentFoodService;
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

/**
 * STT 자유 발화 모드의 음식 검색/혈당 예측 endpoint.
 *
 * <p>X-Agent-Api-Key 인증 (SecurityConfig /api/agent/** chain).
 *
 * <p>대화 예: 사용자 "나 짬뽕 먹을거야" → agent search → predict → "혈당 220mg/dL 까지 오를 수 있어요. 다른 거 어때요?"
 */
@RestController
@RequestMapping("/api/agent/foods")
@RequiredArgsConstructor
@Tag(name = "Agent API", description = "AI Agent 전용: 자유 발화 음식 검색/혈당 예측 (X-Agent-Api-Key 인증)")
public class AgentFoodController {

  private final AgentFoodService service;

  @Operation(summary = "음식 이름 부분 일치 검색 — STT 발화의 음식명을 food_id로 변환")
  @GetMapping("/search")
  public ResponseEntity<List<AgentFoodSearchItem>> search(
      @Parameter(description = "검색어 (예: '짬뽕')") @RequestParam("query") String query,
      @Parameter(description = "최대 N개 (1~10)", example = "5")
          @RequestParam(value = "limit", defaultValue = "5")
          int limit) {
    return ResponseEntity.ok(service.searchFoods(query, limit));
  }

  @Operation(summary = "food_id 기반 혈당 예측 — peak_mgdl / peak_minute / risk_level 반환")
  @GetMapping("/{foodId}/predict-glucose")
  public ResponseEntity<AgentFoodPredictionResult> predict(
      @PathVariable Integer foodId,
      @Parameter(description = "예측 대상 사용자 id") @RequestParam("userId") Integer userId) {
    return ResponseEntity.ok(service.predictForFoodByAgent(userId, foodId));
  }
}
