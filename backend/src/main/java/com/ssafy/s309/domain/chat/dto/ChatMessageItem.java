package com.ssafy.s309.domain.chat.dto;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ChatMessageItem(
    Long id,
    String sender,
    String message,
    String alertType,
    Map<String, Object> displayTrace,
    List<Map<String, String>> options,
    Long parentId,
    String selectedOptionId,
    Boolean isRead,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt) {

  public static ChatMessageItem from(ChatMessage m) {
    return new ChatMessageItem(
        m.getId(),
        m.getSender(),
        m.getMessage(),
        m.getAlertType(),
        m.getDisplayTrace(),
        m.getOptions(),
        m.getParentId(),
        m.getSelectedOptionId(),
        m.getIsRead(),
        m.getResolvedAt(),
        m.getCreatedAt());
  }
}
