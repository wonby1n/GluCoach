package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentStepResponse;
import com.ssafy.s309.domain.health.repository.HealthSnapshotRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentStepService {

  private final HealthSnapshotRepository repo;

  @Transactional(readOnly = true)
  public AgentStepResponse getWindowSteps(Integer userId, LocalDateTime start, LocalDateTime end) {
    if (start.isAfter(end)) {
      throw new IllegalArgumentException("start는 end보다 이전이어야 합니다.");
    }
    int windowSteps = repo.sumStepsInWindow(userId, start, end);
    return new AgentStepResponse(userId, windowSteps, start, end);
  }
}
