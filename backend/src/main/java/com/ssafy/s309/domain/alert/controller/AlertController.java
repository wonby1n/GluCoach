package com.ssafy.s309.domain.alert.controller;

import com.ssafy.s309.domain.alert.dto.AlertListResponse;
import com.ssafy.s309.domain.alert.service.UserAlertService;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert", description = "사용자 알림 CRUD")
public class AlertController {

  private final UserAlertService userAlertService;

  @Operation(
      summary = "내 알림 목록 조회",
      description = "is_read 필터(생략 시 전체), 페이지 기반. unread_count 항상 포함.")
  @GetMapping
  public ResponseEntity<AlertListResponse> list(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam(value = "is_read", required = false) Boolean isRead,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    return ResponseEntity.ok(userAlertService.list(principal.userId(), isRead, page, size));
  }

  @Operation(summary = "알림 읽음 처리", description = "다른 사용자의 알림 접근 시 403.")
  @PatchMapping("/{id}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Integer id) {
    userAlertService.markRead(principal.userId(), id);
    return ResponseEntity.noContent().build();
  }
}
