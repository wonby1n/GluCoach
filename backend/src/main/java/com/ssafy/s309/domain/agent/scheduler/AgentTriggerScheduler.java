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

  // 폴링 주기 — trigger 가 1분 뒤로 예약되는 케이스에서 최악 2분 지연되던 문제 완화.
  // 15초로 단축해 "알림 느리게 옴" 체감 개선.
  @Scheduled(fixedDelay = 15_000)
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
