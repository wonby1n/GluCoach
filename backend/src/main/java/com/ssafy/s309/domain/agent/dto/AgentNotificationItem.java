package com.ssafy.s309.domain.agent.dto;

import com.ssafy.s309.domain.alert.entity.Alert;
import java.time.LocalDateTime;

public record AgentNotificationItem(
    Integer alertId,
    String alertType,
    String message,
    String source,
    LocalDateTime createdAt,
    Boolean isRead) {

  public static AgentNotificationItem from(Alert a) {
    return new AgentNotificationItem(
        a.getId(),
        a.getAlertType(),
        a.getMessage(),
        a.getSource(),
        a.getCreatedAt(),
        a.getIsRead());
  }
}
