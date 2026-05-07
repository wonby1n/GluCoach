package com.ssafy.s309.domain.chat.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.alert.service.AlertChannelResolver;
import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE 룰 발신(sender='system') 메시지의 단일 진입점.
 *
 * <p>기존 AlertCreationService를 chat_messages 도메인으로 이전. 책임 동일: 30분 dedup + INSERT + FCM 발사. 차이점은
 * INSERT 대상이 alerts → chat_messages, sender='system' 고정.
 *
 * <p>호출자: AlertTriggerService(룰 HIGH/LOW), SosService, WeeklyReport 스케줄러 등 BE 내부.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCreationService {

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

  private final ChatMessageService chatMessageService;
  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  @Transactional
  public CreationResult createIfNotDuplicate(Integer userId, String alertType, String message) {
    LocalDateTime since = LocalDateTime.now().minus(DEDUP_WINDOW);
    if (chatMessageService.isDuplicateWithin(userId, alertType, since)) {
      log.debug(
          "system message dedup skip: user={}, type={}, window={}m",
          userId,
          alertType,
          DEDUP_WINDOW.toMinutes());
      return CreationResult.skipped();
    }

    ChatMessage saved = chatMessageService.insertSystem(userId, alertType, message);

    dispatchFcm(userId, alertType, message);

    return CreationResult.created(saved.getId());
  }

  /** 사용자의 active FCM 토큰들에 channel_id 매핑한 발송. 토큰 0개면 silently skip. */
  private void dispatchFcm(Integer userId, String alertType, String message) {
    List<String> tokens =
        tokenRepository.findByUser_IdAndIsActiveTrue(userId).stream()
            .map(NotificationToken::getToken)
            .toList();
    if (tokens.isEmpty()) {
      log.debug("FCM dispatch skip: no active tokens for user={}", userId);
      return;
    }
    String title = AlertChannelResolver.resolveTitle(alertType);
    String channelId = AlertChannelResolver.resolveChannelId(alertType);
    fcmService.sendToTokens(tokens, title, message, channelId);
  }

  public record CreationResult(boolean created, Long chatMessageId) {
    public static CreationResult created(Long id) {
      return new CreationResult(true, id);
    }

    public static CreationResult skipped() {
      return new CreationResult(false, null);
    }

    public Optional<Long> chatMessageIdOptional() {
      return Optional.ofNullable(chatMessageId);
    }
  }
}
