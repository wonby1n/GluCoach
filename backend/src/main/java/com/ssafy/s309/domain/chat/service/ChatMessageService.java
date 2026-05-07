package com.ssafy.s309.domain.chat.service;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.repository.ChatMessageRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public ChatMessage insertAgent(
      Integer userId, String message, Integer alertId, Map<String, Object> displayTrace) {
    return chatMessageRepository.save(
        ChatMessage.builder()
            .userId(userId)
            .sender(ChatMessage.SENDER_AGENT)
            .message(message)
            .alertId(alertId)
            .displayTrace(displayTrace)
            .build());
  }

  @Transactional
  public ChatMessage insertUser(Integer userId, String message, Integer alertId) {
    return chatMessageRepository.save(
        ChatMessage.builder()
            .userId(userId)
            .sender(ChatMessage.SENDER_USER)
            .message(message)
            .alertId(alertId)
            .build());
  }

  @Transactional(readOnly = true)
  public Page<ChatMessage> pageByUser(Integer userId, int page, int size) {
    return chatMessageRepository.findByUserIdOrderByCreatedAtDesc(
        userId, PageRequest.of(page, size));
  }
}
