package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import com.ssafy.s309.domain.cgm.event.GlucoseReceivedEvent;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

  private final AlertCreationService alertCreationService;
  private final AlertRepository alertRepository;

  @Async("alertExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void handle(GlucoseReceivedEvent event) {
    BigDecimal value = event.value();
    Integer userId = event.userId();

    if (value.compareTo(THRESHOLD_HIGH) >= 0) {
      String message = String.format("혈당이 %.0f mg/dL로 기준치(180)를 초과했습니다.", value);
      alertCreationService.createIfNotDuplicate(userId, "HIGH", message, "be");
      log.debug("HIGH alert triggered: user={}, value={}", userId, value);
    } else if (value.compareTo(THRESHOLD_LOW) <= 0) {
      String message = String.format("혈당이 %.0f mg/dL로 기준치(70) 이하입니다.", value);
      alertCreationService.createIfNotDuplicate(userId, "LOW", message, "be");
      log.debug("LOW alert triggered: user={}, value={}", userId, value);
    } else {
      resolveOpenAlerts(userId);
    }
  }

  /** 70 < value < 180 정상 복귀: 해당 사용자의 미해결 HIGH/LOW alert를 모두 종결. */
  private void resolveOpenAlerts(Integer userId) {
    List<Alert> open =
        alertRepository.findByUserIdAndAlertTypeInAndResolvedAtIsNull(userId, RULE_ALERT_TYPES);
    if (open.isEmpty()) {
      return;
    }
    open.forEach(Alert::resolve);
    log.debug("Resolved {} open rule alerts: user={}", open.size(), userId);
  }
}
