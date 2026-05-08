package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.domain.chat.repository.ChatMessageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 혈당 위험 알림(HIGH/LOW) 미확인 방치 시 자동 SOS 트리거.
 *
 * <p>조건: 미읽음 HIGH/LOW 알림이 TRIGGER_DELAY(10분) 이상 방치 + SOS_DEDUP(60분) 내 SOS 미발송.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSosService {

  private static final Duration TRIGGER_DELAY = Duration.ofMinutes(30);
  private static final Duration SOS_DEDUP = Duration.ofMinutes(60);
  private static final List<String> ALERT_TYPES = List.of("HIGH", "LOW");

  private final ChatMessageRepository chatMessageRepository;
  private final SosService sosService;

  @Transactional
  public void checkAndTrigger(Integer userId) {
    LocalDateTime alertThreshold = LocalDateTime.now().minus(TRIGGER_DELAY);
    boolean hasStaleAlert =
        chatMessageRepository
            .existsByUserIdAndMessageTypeInAndIsReadFalseAndResolvedAtIsNullAndCreatedAtBefore(
                userId, ALERT_TYPES, alertThreshold);
    if (!hasStaleAlert) return;

    LocalDateTime sosSince = LocalDateTime.now().minus(SOS_DEDUP);
    boolean sosAlreadySent =
        chatMessageRepository.existsByUserIdAndMessageTypeAndResolvedAtIsNullAndCreatedAtAfter(
            userId, "SOS", sosSince);
    if (sosAlreadySent) {
      log.debug("AutoSOS dedup skip: user={}", userId);
      return;
    }

    sosService.autoSos(userId);
  }
}
