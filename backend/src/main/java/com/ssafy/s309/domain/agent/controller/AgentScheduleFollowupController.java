package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupRequest;
import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupResponse;
import com.ssafy.s309.domain.agent.service.AgentScheduleFollowupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/schedule_followup")
@RequiredArgsConstructor
@Tag(
    name = "Agent API",
    description =
        "Agent #8 — schedule_followup: Agent가 N분 뒤 자기를 다시 깨워달라고 BE에 예약하는 입구."
            + " agent_pending_triggers INSERT만 수행하고, 실제 발화는 기존 1분 폴러가 처리."
            + " (X-Agent-Api-Key 인증)")
public class AgentScheduleFollowupController {

  private final AgentScheduleFollowupService service;

  @Operation(
      summary = "schedule_followup 수신",
      description =
          "delayMinutes 후 시각으로 agent_pending_triggers를 INSERT한다."
              + " trigger_type은 자유 문자열 (예: post_meal_followup). 검증: delayMinutes 1~1440.")
  @PostMapping
  public ResponseEntity<AgentScheduleFollowupResponse> schedule(
      @Valid @RequestBody AgentScheduleFollowupRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.schedule(req));
  }
}
