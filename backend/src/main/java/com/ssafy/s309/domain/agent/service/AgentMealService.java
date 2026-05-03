package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentMealService {

  private final MealRecordRepository repo;

  @Transactional(readOnly = true)
  public List<AgentMealItem> getMeals(Integer userId, LocalDate date) {
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.atTime(LocalTime.MAX);
    return repo.findAgentMealsByUserAndRange(userId, from, to);
  }
}
