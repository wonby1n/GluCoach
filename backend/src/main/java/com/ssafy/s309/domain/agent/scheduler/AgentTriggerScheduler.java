package com.ssafy.s309.domain.agent.scheduler;

import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import com.ssafy.s309.domain.agent.service.AgentTriggerDispatcher;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTriggerScheduler {

  // DB·앱 서버 TZ 불일치로 trigger.scheduled_at 비교가 어긋나는 사고를 막기 위해 KST 명시.
  // PredictionService / WeeklyReportScheduler 와 동일 패턴.
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final AgentPendingTriggerRepository triggerRepository;
  private final AgentTriggerDispatcher dispatcher;

  @Scheduled(fixedDelay = 60_000)
  public void dispatchPendingTriggers() {
    List<AgentPendingTrigger> pending =
        triggerRepository.findPendingTriggers(LocalDateTime.now(KST));

    if (pending.isEmpty()) {
      return;
    }

    log.info("pending triggers found: {}", pending.size());

    for (AgentPendingTrigger trigger : pending) {
      try {
        dispatcher.dispatch(trigger);
        trigger.markDispatched();
        triggerRepository.save(trigger); // JpaRepository.save()는 자체 트랜잭션 보유
      } catch (Exception e) {
        log.warn(
            "trigger dispatch failed — will retry next cycle: id={} error={}",
            trigger.getId(),
            e.getMessage());
      }
    }
  }
}
