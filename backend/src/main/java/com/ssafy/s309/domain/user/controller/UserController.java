package com.ssafy.s309.domain.user.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.user.dto.GuardianRequest;
import com.ssafy.s309.domain.user.dto.GuardianResponse;
import com.ssafy.s309.domain.user.dto.SettingsResponse;
import com.ssafy.s309.domain.user.dto.SettingsUpdateRequest;
import com.ssafy.s309.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 설정 및 보호자 관리 API")
public class UserController {

  private final UserService userService;

  // ── Settings ──────────────────────────────────────────────

  @Operation(summary = "설정 조회")
  @GetMapping("/settings")
  public ResponseEntity<SettingsResponse> getSettings(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(userService.getSettings(principal.userId()));
  }

  @Operation(summary = "설정 수정")
  @PutMapping("/settings")
  public ResponseEntity<SettingsResponse> updateSettings(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestBody SettingsUpdateRequest request) {
    return ResponseEntity.ok(userService.updateSettings(principal.userId(), request));
  }

  // ── Guardian ──────────────────────────────────────────────

  @Operation(summary = "보호자 목록 조회")
  @GetMapping("/guardians")
  public ResponseEntity<List<GuardianResponse>> getGuardians(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(userService.getGuardians(principal.userId()));
  }

  @Operation(summary = "보호자 추가")
  @PostMapping("/guardians")
  public ResponseEntity<GuardianResponse> createGuardian(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody GuardianRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.createGuardian(principal.userId(), request));
  }

  @Operation(summary = "보호자 수정")
  @PutMapping("/guardians/{wardGuardianId}")
  public ResponseEntity<GuardianResponse> updateGuardian(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @PathVariable Integer wardGuardianId,
      @Valid @RequestBody GuardianRequest request) {
    return ResponseEntity.ok(
        userService.updateGuardian(principal.userId(), wardGuardianId, request));
  }

  @Operation(summary = "보호자 삭제")
  @DeleteMapping("/guardians/{wardGuardianId}")
  public ResponseEntity<Void> deleteGuardian(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @PathVariable Integer wardGuardianId) {
    userService.deleteGuardian(principal.userId(), wardGuardianId);
    return ResponseEntity.noContent().build();
  }
}
