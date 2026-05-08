package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.domain.cgm.event.GlucoseReceivedEvent;
import com.ssafy.s309.domain.chat.service.ChatMessageCreationService;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 혈당 저장 커밋 직후 HIGH/LOW 룰 알림 판단. AFTER_COMMIT + @Async: 원본 트랜잭션과 분리된 스레드에서 실행 → HTTP 응답 블로킹 없음. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertTriggerService {

  private static final BigDecimal THRESHOLD_HIGH = new BigDecimal("180");
  private static final BigDecimal THRESHOLD_LOW = new BigDecimal("70");
  private static final List<String> RULE_ALERT_TYPES = List.of("HIGH", "LOW");

  private final ChatMessageCreationService chatMessageCreationService;
  private final ChatMessageService chatMessageService;
  private final AutoSosService autoSosService;

  @Async("alertExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(GlucoseReceivedEvent event) {
    BigDecimal value = event.value();
    Integer userId = event.userId();

    if (value.compareTo(THRESHOLD_HIGH) >= 0) {
      String message = String.format("혈당이 %.0f mg/dL로 기준치(180)를 초과했습니다.", value);
      chatMessageCreationService.createIfNotDuplicate(userId, "HIGH", message);
      autoSosService.checkAndTrigger(userId);
      log.debug("HIGH alert triggered: user={}, value={}", userId, value);
    } else if (value.compareTo(THRESHOLD_LOW) <= 0) {
      String message = String.format("혈당이 %.0f mg/dL로 기준치(70) 이하입니다.", value);
      chatMessageCreationService.createIfNotDuplicate(userId, "LOW", message);
      autoSosService.checkAndTrigger(userId);
      log.debug("LOW alert triggered: user={}, value={}", userId, value);
    } else {
      resolveOpenAlerts(userId);
    }
  }

  /** 70 < value < 180 정상 복귀: 해당 사용자의 미해결 HIGH/LOW chat 메시지를 모두 종결. */
  private void resolveOpenAlerts(Integer userId) {
    int resolved = chatMessageService.resolveOpenRule(userId, RULE_ALERT_TYPES);
    if (resolved > 0) {
      log.debug("Resolved {} open rule chat messages: user={}", resolved, userId);
    }
  }
}
