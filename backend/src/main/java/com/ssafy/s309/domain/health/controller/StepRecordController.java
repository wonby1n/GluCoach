package com.ssafy.s309.domain.health.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.health.dto.StepRecordBatchRequest;
import com.ssafy.s309.domain.health.dto.StepRecordBatchResponse;
import com.ssafy.s309.domain.health.service.StepRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/step-records")
@RequiredArgsConstructor
@Tag(name = "StepRecords", description = "걸음수 시계열 (5분 배치 INSERT)")
public class StepRecordController {

  private final StepRecordService service;

  @Operation(
      summary = "걸음수 배치 INSERT",
      description =
          "프론트가 5분마다 모은 누적 걸음수 5건을 한 번에 전송. "
              + "(user_id, recorded_at) 중복은 ON CONFLICT DO NOTHING으로 무시되어 멱등.")
  @PostMapping
  public ResponseEntity<StepRecordBatchResponse> saveBatch(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody StepRecordBatchRequest request) {
    return ResponseEntity.ok(service.saveBatch(principal.userId(), request));
  }
}
