package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentStepResponse;
import com.ssafy.s309.domain.agent.service.AgentStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/steps")
@RequiredArgsConstructor
@Tag(name = "AgentSteps", description = "AI Agent 전용: 시간 윈도우 걸음수 조회 (X-Agent-Api-Key 인증)")
public class AgentStepController {

  private final AgentStepService service;

  @Operation(
      summary = "시간 윈도우 걸음수 조회",
      description =
          "step_records 테이블에서 (user_id, [start, end]) 범위의 MAX(steps_total) - MIN(steps_total) "
              + "을 계산해 구간 걸음수를 반환. 데이터 0건이면 windowSteps=0.")
  @GetMapping
  public ResponseEntity<AgentStepResponse> getWindowSteps(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId,
      @Parameter(description = "구간 시작 (ISO 8601)", example = "2026-05-02T13:00:00")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime start,
      @Parameter(description = "구간 종료 (ISO 8601)", example = "2026-05-02T14:00:00")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime end) {
    return ResponseEntity.ok(service.getWindowSteps(userId, start, end));
  }
}
