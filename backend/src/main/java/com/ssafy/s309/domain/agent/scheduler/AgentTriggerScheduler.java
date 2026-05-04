package com.ssafy.s309.domain.agent.scheduler;

import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import com.ssafy.s309.domain.agent.service.AgentTriggerDispatcher;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTriggerScheduler {

  private final AgentPendingTriggerRepository triggerRepository;
  private final AgentTriggerDispatcher dispatcher;

  @Scheduled(fixedDelay = 60_000)
  public void dispatchPendingTriggers() {
    List<AgentPendingTrigger> pending = triggerRepository.findPendingTriggers(LocalDateTime.now());

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
