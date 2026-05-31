package com.ssafy.s309.domain.agent.controller;

import com.ssafy.s309.domain.agent.dto.AgentUserProfileResponse;
import com.ssafy.s309.domain.agent.service.AgentUserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/user-profile")
@RequiredArgsConstructor
@Tag(
    name = "Agent API",
    description = "AI Agent 전용: 사용자 프로필(당뇨 유형 + 목표 혈당 범위) 조회 (X-Agent-Api-Key 인증)")
public class AgentUserProfileController {

  private final AgentUserProfileService service;

  @Operation(
      summary = "사용자 프로필 조회",
      description =
          "users 테이블의 diabetes_type / target_low / target_high를 반환. "
              + "프로필 미입력 사용자는 해당 필드가 null로 반환됨.")
  @GetMapping
  public ResponseEntity<AgentUserProfileResponse> getUserProfile(
      @Parameter(description = "조회 대상 사용자 id", example = "3") @RequestParam("user_id")
          Integer userId) {
    return ResponseEntity.ok(service.getUserProfile(userId));
  }
}
