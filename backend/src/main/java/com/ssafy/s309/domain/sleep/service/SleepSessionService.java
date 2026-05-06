package com.ssafy.s309.domain.sleep.service;

import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import com.ssafy.s309.domain.sleep.dto.SleepSessionCreateRequest;
import com.ssafy.s309.domain.sleep.dto.SleepSessionResponse;
import com.ssafy.s309.domain.sleep.entity.SleepSession;
import com.ssafy.s309.domain.sleep.repository.SleepSessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SleepSessionService {

  private final SleepSessionRepository sleepSessionRepository;
  private final AgentPendingTriggerRepository triggerRepository;

  @Transactional
  public SleepSessionResponse create(Integer userId, SleepSessionCreateRequest req) {
    if (!req.endedAt().isAfter(req.startedAt())) {
      throw new IllegalArgumentException("endedAt must be after startedAt");
    }

    Optional<SleepSession> existing =
        sleepSessionRepository.findByUserIdAndStartedAt(userId, req.startedAt());
    if (existing.isPresent()) {
      return SleepSessionResponse.from(existing.get());
    }

    SleepSession saved =
        sleepSessionRepository.save(
            SleepSession.builder()
                .userId(userId)
                .startedAt(req.startedAt())
                .endedAt(req.endedAt())
                .source(req.source())
                .build());

    triggerRepository.save(
        AgentPendingTrigger.builder()
            .userId(userId)
            .triggerType(AgentPendingTrigger.TYPE_WAKE_UP)
            .referenceId(saved.getId())
            .scheduledAt(LocalDateTime.now())
            .build());

    return SleepSessionResponse.from(saved);
  }
}
