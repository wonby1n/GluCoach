package com.ssafy.s309.domain.chat.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.alert.service.AlertChannelResolver;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * chat_messages INSERT 직후 본인에게 FCM 발사하는 단일 진입점.
 *
 * <p>중복되던 dispatchFcm 로직(AgentNotificationService, ChatMessageCreationService)을 통합.
 *
 * <p>SOS의 보호자 발송은 별 도메인 책임이라 SosService에 그대로 둠.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatFcmDispatcher {

  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  /**
   * 사용자의 active 토큰들에 channel_id 매핑한 발송. 토큰 0개면 silently skip.
   *
   * <p>FCM data payload 키 alertType은 FE 호환을 위해 그대로 유지 (값은 messageType).
   */
  public void dispatch(Integer userId, String messageType, String message) {
    List<String> tokens =
        tokenRepository.findByUser_IdAndIsActiveTrue(userId).stream()
            .map(NotificationToken::getToken)
            .toList();
    if (tokens.isEmpty()) {
      log.debug("FCM dispatch skip: no active tokens for user={}", userId);
      return;
    }
    String title = AlertChannelResolver.resolveTitle(messageType);
    String channelId = AlertChannelResolver.resolveChannelId(messageType);
    fcmService.sendToTokens(tokens, title, message, channelId);
  }
}
