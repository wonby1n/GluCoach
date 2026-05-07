package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;

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
        m.getAlertType(),
        m.getMessage(),
        m.getSource(),
        m.getCreatedAt(),
        m.getIsRead());
  }
}
