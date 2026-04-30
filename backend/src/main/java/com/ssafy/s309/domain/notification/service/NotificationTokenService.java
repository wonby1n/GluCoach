package com.ssafy.s309.domain.notification.service;

import com.ssafy.s309.common.service.FcmService;
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
  public void saveToken(User user, String token, String deviceType) {
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
