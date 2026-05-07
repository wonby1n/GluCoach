package com.ssafy.s309.domain.chat.repository;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

  Page<ChatMessage> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
}
