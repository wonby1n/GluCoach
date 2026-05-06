package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupRequest;
import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupResponse;
import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent #8 — schedule_followup 수신 서비스.
 *
 * <p>Agent가 "{@code delayMinutes} 분 후에 다시 깨워달라"고 예약 요청 → {@code agent_pending_triggers} INSERT만 수행.
 * 실제 발화는 기존 {@link com.ssafy.s309.domain.agent.scheduler.AgentTriggerScheduler}가 1분 폴링으로 자동 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AgentScheduleFollowupService {

  private final AgentPendingTriggerRepository repository;

  @Transactional
  public AgentScheduleFollowupResponse schedule(AgentScheduleFollowupRequest req) {
    LocalDateTime scheduledAt = LocalDateTime.now().plusMinutes(req.delayMinutes());
    AgentPendingTrigger saved =
        repository.save(
            AgentPendingTrigger.builder()
                .userId(req.userId())
                .triggerType(req.triggerType())
                .referenceId(req.referenceId())
                .scheduledAt(scheduledAt)
                .reason(req.reason())
                .build());
    return new AgentScheduleFollowupResponse(saved.getId(), saved.getScheduledAt());
  }
}
