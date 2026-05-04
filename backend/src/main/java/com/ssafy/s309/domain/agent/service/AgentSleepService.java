package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentSleepResponse;
import com.ssafy.s309.domain.health.repository.DailyHealthSummaryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentSleepService {

  private final DailyHealthSummaryRepository repo;

  @Transactional(readOnly = true)
  public AgentSleepResponse getSleep(Integer userId, LocalDate date) {
    Integer sleepMinutes =
        repo.findByUserIdAndDate(userId, date)
            .map(s -> s.getSleepMinutes() != null ? s.getSleepMinutes() : 0)
            .orElse(0);

    BigDecimal avg = repo.findAverageSleepMinutesLast7Days(userId, date);
    BigDecimal averageSleepMinutes =
        avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

    return new AgentSleepResponse(date, sleepMinutes, averageSleepMinutes);
  }
}
