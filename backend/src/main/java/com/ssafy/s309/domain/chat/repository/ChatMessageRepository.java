package com.ssafy.s309.domain.chat.repository;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  Page<ChatMessage> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

  long countByUserIdAndIsReadFalse(Integer userId);

  Optional<ChatMessage> findByIdAndUserId(Long id, Integer userId);

  /** dedup 30분 윈도우 — 같은 (user, alert_type) 미해결 메시지가 since 이후에 존재하는가. */
  boolean existsByUserIdAndAlertTypeAndResolvedAtIsNullAndCreatedAtAfter(
      Integer userId, String alertType, LocalDateTime since);

  /** 정상 복귀 시 해소할 활성 룰 메시지 조회. */
  List<ChatMessage> findByUserIdAndAlertTypeInAndResolvedAtIsNull(
      Integer userId, List<String> alertTypes);

  /** Agent #6 notification_history — alert_type이 있는 메시지(agent/system 발신)만, 최근 N시간. */
  List<ChatMessage> findByUserIdAndAlertTypeIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(
      Integer userId, LocalDateTime since);
}
