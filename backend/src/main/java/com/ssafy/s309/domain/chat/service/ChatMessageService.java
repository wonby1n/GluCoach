package com.ssafy.s309.domain.chat.service;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.repository.ChatMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * chat_messages 도메인 단일 진입점.
 *
 * <p>책임: 양방향 메시지 INSERT(agent/system/user), 읽음/해소 라이프사이클, 페이징 조회, dedup 게이트.
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

  private final ChatMessageRepository chatMessageRepository;

  /** Agent 발신 메시지. options 정확히 3개 필수. */
  @Transactional
  public ChatMessage insertAgent(
      Integer userId,
      String messageType,
      String message,
      List<Map<String, String>> options,
      Map<String, Object> displayTrace) {
    if (options == null || options.size() != 3) {
      throw new IllegalArgumentException("agent message requires exactly 3 options");
    }
    return chatMessageRepository.save(
        ChatMessage.builder()
            .userId(userId)
            .sender(ChatMessage.SENDER_AGENT)
            .messageType(messageType)
            .message(message)
            .options(options)
            .displayTrace(displayTrace)
            .source(ChatMessage.SOURCE_AGENT)
            .build());
  }

  /** BE 룰 발신 메시지 (HIGH/LOW/SOS/WEEKLY_REPORT 등). */
  @Transactional
  public ChatMessage insertSystem(Integer userId, String messageType, String message) {
    return chatMessageRepository.save(
        ChatMessage.builder()
            .userId(userId)
            .sender(ChatMessage.SENDER_SYSTEM)
            .messageType(messageType)
            .message(message)
            .source(ChatMessage.SOURCE_BE)
            .build());
  }

  /** 사용자 응답. parent agent 메시지의 options 중 하나 선택. message는 선택된 option의 label로 채움 (호출 측에서 lookup). */
  @Transactional
  public ChatMessage insertUserReply(
      Integer userId, Long parentId, String selectedOptionId, String labelMessage) {
    return chatMessageRepository.save(
        ChatMessage.builder()
            .userId(userId)
            .sender(ChatMessage.SENDER_USER)
            .message(labelMessage)
            .parentId(parentId)
            .selectedOptionId(selectedOptionId)
            .build());
  }

  /**
   * 사용자가 parent agent 메시지의 옵션 중 하나를 선택했을 때 호출. parent 검증 + label lookup + insertUserReply 위임.
   *
   * <ul>
   *   <li>parent가 본인 메시지가 아니면 403
   *   <li>parent.sender != 'agent'이면 400 (시스템/사용자 메시지에는 응답 불가)
   *   <li>optionId가 parent.options에 없으면 400
   * </ul>
   */
  @Transactional
  public ChatMessage replyByOption(Integer userId, Long parentId, String optionId) {
    ChatMessage parent =
        chatMessageRepository
            .findByIdAndUserId(parentId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    if (!ChatMessage.SENDER_AGENT.equals(parent.getSender()) || parent.getOptions() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "parent must be an agent message with options");
    }
    String label =
        parent.getOptions().stream()
            .filter(opt -> optionId.equals(opt.get("id")))
            .map(opt -> opt.get("label"))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "optionId not found in parent.options"));
    return insertUserReply(userId, parentId, optionId, label);
  }

  /** 본인 메시지 페이징 (최신순). */
  @Transactional(readOnly = true)
  public Page<ChatMessage> pageByUser(Integer userId, int page, int size) {
    return chatMessageRepository.findByUserIdOrderByCreatedAtDesc(
        userId, PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public long countUnread(Integer userId) {
    return chatMessageRepository.countByUserIdAndIsReadFalse(userId);
  }

  @Transactional
  public void markRead(Integer userId, Long chatMessageId) {
    ChatMessage msg =
        chatMessageRepository
            .findByIdAndUserId(chatMessageId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    msg.markRead();
  }

  /** dedup 30분 윈도우 체크. */
  @Transactional(readOnly = true)
  public boolean isDuplicateWithin(Integer userId, String messageType, LocalDateTime since) {
    return chatMessageRepository.existsByUserIdAndMessageTypeAndResolvedAtIsNullAndCreatedAtAfter(
        userId, messageType, since);
  }

  /** 룰 알림 정상복귀 — 미해결 메시지 모두 resolved_at 갱신. */
  @Transactional
  public int resolveOpenRule(Integer userId, List<String> messageTypes) {
    List<ChatMessage> open =
        chatMessageRepository.findByUserIdAndMessageTypeInAndResolvedAtIsNull(userId, messageTypes);
    open.forEach(ChatMessage::resolve);
    return open.size();
  }
}
