package com.ssafy.s309.domain.sleep.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.sleep.dto.SleepSessionCreateRequest;
import com.ssafy.s309.domain.sleep.dto.SleepSessionResponse;
import com.ssafy.s309.domain.sleep.service.SleepSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sleep-sessions")
@RequiredArgsConstructor
@Tag(
    name = "건강 데이터",
    description =
        "워치/Health Connect에서 동기화한 개별 수면 세션 저장. 세션 INSERT 시 wake_up 트리거 자동 예약 → Agent 기상 코칭.")
public class SleepSessionController {

  private final SleepSessionService service;

  @Operation(
      summary = "수면 세션 저장",
      description =
          "동일 (user_id, started_at) 조합 중복 호출 시 기존 row 반환 (재동기화 멱등). "
              + "세션 저장 성공 시 wake_up 트리거가 즉시 발화 시각으로 INSERT됨.")
  @PostMapping
  public ResponseEntity<SleepSessionResponse> create(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody SleepSessionCreateRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.userId(), req));
  }
}
