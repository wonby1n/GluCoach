package com.ssafy.s309.domain.health.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.health.dto.HealthSnapshotBatchRequest;
import com.ssafy.s309.domain.health.dto.HealthSnapshotBatchResponse;
import com.ssafy.s309.domain.health.service.HealthSnapshotService;
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
@RequestMapping("/api/health/snapshots")
@RequiredArgsConstructor
@Tag(name = "HealthSnapshots", description = "1분 polling 메트릭 시계열 (5분 batch INSERT)")
public class HealthSnapshotController {

  private final HealthSnapshotService service;

  @Operation(
      summary = "헬스 스냅샷 배치 INSERT",
      description =
          "프론트가 5분 동안 1분마다 모은 메트릭(steps_total, calories_burned, heart_rate)을 한 번에 전송. "
              + "(user_id, recorded_at) 중복은 ON CONFLICT DO NOTHING으로 무시되어 멱등.")
  @PostMapping
  public ResponseEntity<HealthSnapshotBatchResponse> saveBatch(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody HealthSnapshotBatchRequest request) {
    return ResponseEntity.ok(service.saveBatch(principal.userId(), request));
  }
}
