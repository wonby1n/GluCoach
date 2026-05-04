package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentGlucosePoint;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentGlucoseService {

  private final GlucoseRecordRepository repo;

  @Transactional(readOnly = true)
  public List<AgentGlucosePoint> getGlucose(
      Integer userId, LocalDateTime startTime, LocalDateTime endTime) {
    if (startTime.isAfter(endTime)) {
      throw new IllegalArgumentException("start_time은 end_time보다 이전이어야 합니다.");
    }
    return repo
        .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, startTime, endTime)
        .stream()
        .map(AgentGlucosePoint::from)
        .toList();
  }
}
