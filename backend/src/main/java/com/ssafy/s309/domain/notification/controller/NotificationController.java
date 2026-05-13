package com.ssafy.s309.domain.notification.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.notification.dto.FcmTokenRequest;
import com.ssafy.s309.domain.notification.entity.DeviceType;
import com.ssafy.s309.domain.notification.service.NotificationTokenService;
import com.ssafy.s309.domain.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/fcm-token")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "FCM 토큰 관리 API")
public class NotificationController {

  private final NotificationTokenService notificationTokenService;
  private final UserRepository userRepository;

  @Operation(summary = "FCM 토큰 등록/갱신")
  @PutMapping
  public ResponseEntity<Void> saveToken(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody FcmTokenRequest request) {
    userRepository
        .findById(principal.userId())
        .ifPresent(
            user ->
                notificationTokenService.saveToken(
                    user,
                    request.token(),
                    request.deviceType() != null ? request.deviceType() : DeviceType.ANDROID));
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "FCM 토큰 비활성화 (로그아웃 시)",
      description =
          "로그아웃하는 기기의 FCM 토큰만 is_active=false 로 전환. 다른 기기 토큰은 그대로 유지. "
              + "이 기기에 다른 사용자가 로그인했을 때 이전 사용자에게 알림이 새는 사고를 막는다.")
  @DeleteMapping
  public ResponseEntity<Void> deactivateToken(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody FcmTokenRequest request) {
    userRepository
        .findById(principal.userId())
        .ifPresent(user -> notificationTokenService.deactivateToken(user, request.token()));
    return ResponseEntity.ok().build();
  }
}
