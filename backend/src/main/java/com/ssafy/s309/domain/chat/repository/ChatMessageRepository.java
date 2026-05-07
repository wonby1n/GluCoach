package com.ssafy.s309.domain.chat.repository;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  Page<ChatMessage> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

  long countByUserIdAndIsReadFalse(Integer userId);

  Optional<ChatMessage> findByIdAndUserId(Long id, Integer userId);

  /** dedup 30분 윈도우 — 같은 (user, message_type) 미해결 메시지가 since 이후에 존재하는가. */
  boolean existsByUserIdAndMessageTypeAndResolvedAtIsNullAndCreatedAtAfter(
      Integer userId, String messageType, LocalDateTime since);

  /** 정상 복귀 시 해소할 활성 룰 메시지 조회. */
  List<ChatMessage> findByUserIdAndMessageTypeInAndResolvedAtIsNull(
      Integer userId, List<String> messageTypes);

  /** Agent #6 notification_history — message_type이 있는 메시지(agent/system 발신)만, 최근 N시간. */
  List<ChatMessage> findByUserIdAndMessageTypeIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(
      Integer userId, LocalDateTime since);

  /** 본인 미읽음 일괄 읽음 처리 — 채팅방 진입 시 호출. */
  @Modifying
  @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.userId = ?1 AND m.isRead = false")
  int markAllReadByUserId(Integer userId);
}
