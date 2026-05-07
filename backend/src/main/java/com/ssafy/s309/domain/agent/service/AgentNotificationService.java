package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateRequest;
import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateResponse;
import com.ssafy.s309.domain.agent.dto.AgentNotificationItem;
import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.repository.ChatMessageRepository;
import com.ssafy.s309.domain.chat.service.ChatFcmDispatcher;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent #6 (notification_history) + #7 (send_notification).
 *
 * <p>chat_messages 직접 INSERT + dedup. AGENT_* prefix만 허용 (룰 type 보호). 30분 dedup 통과 시 INSERT + FCM
 * 발사. dedup skip 시 200 + skipped:true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentNotificationService {

  private static final String AGENT_PREFIX = "AGENT_";
  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

  private final ChatMessageService chatMessageService;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatFcmDispatcher chatFcmDispatcher;

  @Transactional(readOnly = true)
  public List<AgentNotificationItem> listRecent(Integer userId, Integer hours) {
    int safeHours = (hours == null || hours <= 0) ? 24 : Math.min(hours, 24 * 30);
    LocalDateTime since = LocalDateTime.now().minusHours(safeHours);
    return chatMessageRepository
        .findByUserIdAndMessageTypeIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)
        .stream()
        .map(AgentNotificationItem::from)
        .toList();
  }

  @Transactional
  public CreationOutcome send(AgentNotificationCreateRequest req) {
    if (req.alertType() == null || !req.alertType().startsWith(AGENT_PREFIX)) {
      throw new IllegalArgumentException(
          "alert_type은 AGENT_ prefix로 시작해야 합니다. (룰 type은 BE 룰 트리거에서만 INSERT)");
    }

    LocalDateTime since = LocalDateTime.now().minus(DEDUP_WINDOW);
    if (chatMessageService.isDuplicateWithin(req.userId(), req.alertType(), since)) {
      log.debug(
          "Agent notification dedup skip: user={}, type={}, window={}m",
          req.userId(),
          req.alertType(),
          DEDUP_WINDOW.toMinutes());
      return new CreationOutcome(false, AgentNotificationCreateResponse.ofSkipped());
    }

    ChatMessage saved =
        chatMessageService.insertAgent(
            req.userId(), req.alertType(), req.message(), req.options(), req.displayTrace());

    chatFcmDispatcher.dispatch(req.userId(), req.alertType(), req.message());

    return new CreationOutcome(true, AgentNotificationCreateResponse.ofCreated(saved.getId()));
  }

  /** Controller가 status code(201 vs 200) 분기에 사용. */
  public record CreationOutcome(boolean created, AgentNotificationCreateResponse body) {}
}
