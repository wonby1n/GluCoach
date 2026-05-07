package com.ssafy.s309.domain.chat.service;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE 룰 발신(sender='system') 메시지의 단일 진입점.
 *
 * <p>30분 dedup 통과 시 chat_messages INSERT + FCM 발사. INSERT 대상은 chat_messages, sender='system' 고정.
 *
 * <p>호출자: AlertTriggerService(룰 HIGH/LOW), WeeklyReport 스케줄러 등 BE 내부.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCreationService {

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

  private final ChatMessageService chatMessageService;
  private final ChatFcmDispatcher chatFcmDispatcher;

  @Transactional
  public CreationResult createIfNotDuplicate(Integer userId, String messageType, String message) {
    LocalDateTime since = LocalDateTime.now().minus(DEDUP_WINDOW);
    if (chatMessageService.isDuplicateWithin(userId, messageType, since)) {
      log.debug(
          "system message dedup skip: user={}, type={}, window={}m",
          userId,
          messageType,
          DEDUP_WINDOW.toMinutes());
      return CreationResult.skipped();
    }

    ChatMessage saved = chatMessageService.insertSystem(userId, messageType, message);

    chatFcmDispatcher.dispatch(userId, messageType, message);

    return CreationResult.created(saved.getId());
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
