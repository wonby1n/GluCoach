package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;

/**
 * Agent #6 notification_history 응답 DTO. JSON 키 alertType은 외부 호환을 위해 유지 (Agent Python 코드의 키 의존). 값은
 * 내부 messageType에서 매핑.
 */
public record AgentNotificationItem(
    Long chatMessageId,
    String alertType,
    String message,
    String source,
    LocalDateTime createdAt,
    Boolean isRead) {

  public static AgentNotificationItem from(ChatMessage m) {
    return new AgentNotificationItem(
        m.getId(),
        m.getMessageType(),
        m.getMessage(),
        m.getSource(),
        m.getCreatedAt(),
        m.getIsRead());
  }
}
