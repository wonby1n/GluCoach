package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentGlucosePoint;
import com.ssafy.s309.domain.agent.service.AgentGlucoseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/glucose")
@RequiredArgsConstructor
@Tag(name = "Agent API", description = "AI Agent 전용: 시계열 혈당 raw 조회 (X-Agent-Api-Key 인증)")
public class AgentGlucoseController {

  private final AgentGlucoseService service;

  @Operation(
      summary = "시계열 혈당 raw 조회",
      description =
          "glucose_records 테이블에서 (user_id, [start_time, end_time]) 범위의 측정값을 measured_at "
              + "오름차순으로 반환. 시계열 raw 그대로 노출. start_time > end_time → 400.")
  @GetMapping
  public ResponseEntity<List<AgentGlucosePoint>> getGlucose(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId,
      @Parameter(description = "구간 시작 (ISO 8601)", example = "2026-05-02T00:00:00")
          @RequestParam("start_time")
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startTime,
      @Parameter(description = "구간 종료 (ISO 8601)", example = "2026-05-02T23:59:59")
          @RequestParam("end_time")
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endTime) {
    return ResponseEntity.ok(service.getGlucose(userId, startTime, endTime));
  }
}
