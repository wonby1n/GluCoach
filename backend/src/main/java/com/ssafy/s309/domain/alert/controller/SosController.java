package com.ssafy.s309.domain.alert.controller;

import com.ssafy.s309.domain.alert.dto.SosRequest;
import com.ssafy.s309.domain.alert.dto.SosResponse;
import com.ssafy.s309.domain.alert.service.SosService;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
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
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 알림 CRUD")
public class SosController {

  private final SosService sosService;

  @Operation(
      summary = "SOS 긴급 요청",
      description = "위치 정보를 수신해 alerts에 기록하고, 1순위 보호자에게 즉시 FCM 발송. " + "등록된 보호자가 없으면 알림만 기록.")
  @PostMapping("/sos")
  public ResponseEntity<SosResponse> sos(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody SosRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(sosService.sos(principal.userId(), request));
  }
}
