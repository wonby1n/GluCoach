package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentSleepResponse;
import com.ssafy.s309.domain.agent.service.AgentSleepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/sleep")
@RequiredArgsConstructor
@Tag(name = "AgentSleep", description = "AI Agent 전용: 일별 수면 + 7일 평균 (X-Agent-Api-Key 인증)")
public class AgentSleepController {

  private final AgentSleepService service;

  @Operation(
      summary = "일별 수면 + 7일 평균 조회",
      description =
          "daily_health_summaries에서 (user_id, date) 단일 일별 sleep_minutes를 반환하고, "
              + "최근 7일([date-6, date]) sleep_minutes IS NOT NULL 행 평균을 함께 반환. "
              + "데이터 없음 → 0 반환.")
  @GetMapping
  public ResponseEntity<AgentSleepResponse> getSleep(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId,
      @Parameter(description = "기준일 (YYYY-MM-DD)", example = "2026-05-03")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(service.getSleep(userId, date));
  }
}
