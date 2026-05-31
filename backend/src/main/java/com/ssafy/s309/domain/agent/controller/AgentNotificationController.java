package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateRequest;
import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateResponse;
import com.ssafy.s309.domain.agent.dto.AgentNotificationItem;
import com.ssafy.s309.domain.agent.service.AgentNotificationService;
import com.ssafy.s309.domain.agent.service.AgentNotificationService.CreationOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/notifications")
@RequiredArgsConstructor
@Tag(name = "Agent API", description = "AI Agent 전용: 알림 이력 조회(#6) + 알림 발송(#7) (X-Agent-Api-Key 인증)")
public class AgentNotificationController {

  private final AgentNotificationService service;

  @Operation(
      summary = "Agent #6 — 최근 알림 이력 조회 (notification_history)",
      description =
          "user_id 사용자의 최근 N시간 알림(soft-delete 제외)을 created_at DESC로 반환. "
              + "hours 미지정 시 24, 최대 720(30일).")
  @GetMapping
  public ResponseEntity<List<AgentNotificationItem>> list(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId,
      @Parameter(description = "최근 N시간 (기본 24, 최대 720)", example = "24")
          @RequestParam(value = "hours", required = false)
          Integer hours) {
    return ResponseEntity.ok(service.listRecent(userId, hours));
  }

  @Operation(
      summary = "Agent #7 — 알림 발송 (send_notification)",
      description =
          "AGENT_* prefix만 허용 (룰 type 보호). 30분 dedup 통과 시 chat_messages INSERT + FCM 발사. "
              + "options 정확히 3개 필수. dedup skip 시 200 + skipped:true 반환. "
              + "AGENT_ prefix / options 길이 위반 시 400.")
  @PostMapping
  public ResponseEntity<AgentNotificationCreateResponse> send(
      @Valid @RequestBody AgentNotificationCreateRequest req) {
    CreationOutcome outcome = service.send(req);
    return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(outcome.body());
  }
}
