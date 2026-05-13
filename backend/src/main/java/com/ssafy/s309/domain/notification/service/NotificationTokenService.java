package com.ssafy.s309.domain.notification.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.notification.entity.DeviceType;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.s309.domain.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationTokenService {

  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  @Transactional
  public void saveToken(User user, String token, DeviceType deviceType) {
    tokenRepository
        .findByUserAndDeviceType(user, deviceType)
        .ifPresentOrElse(
            existing -> existing.updateToken(token),
            () ->
                tokenRepository.save(
                    NotificationToken.builder()
                        .user(user)
                        .token(token)
                        .deviceType(deviceType)
                        .build()));
  }

  /**
   * 로그아웃 시점에 호출 — 이 기기의 토큰만 비활성화한다.
   *
   * <p>같은 사용자의 다른 기기 토큰은 건드리지 않고, 사용자가 모르는 사이 다른 기기로 다른 사용자가 로그인했을 때 이전 사용자의 알림이 새 사용자에게 가는 사고를
   * 막는다.
   */
  @Transactional
  public void deactivateToken(User user, String token) {
    tokenRepository.findByUserAndToken(user, token).ifPresent(NotificationToken::deactivate);
  }

  public void sendAlert(User user, String title, String body) {
    List<String> tokens =
        tokenRepository.findByUserAndIsActiveTrue(user).stream()
            .map(NotificationToken::getToken)
            .toList();
    if (!tokens.isEmpty()) {
      fcmService.sendToTokens(tokens, title, body);
    }
  }
}
