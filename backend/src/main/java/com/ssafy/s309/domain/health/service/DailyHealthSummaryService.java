package com.ssafy.s309.domain.health.service;

import com.ssafy.s309.domain.health.dto.DailyHealthSummaryResponse;
import com.ssafy.s309.domain.health.dto.DailyHealthSummaryUpsertRequest;
import com.ssafy.s309.domain.health.entity.DailyHealthSummary;
import com.ssafy.s309.domain.health.repository.DailyHealthSummaryRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyHealthSummaryService {

  private final DailyHealthSummaryRepository repo;

  @Transactional
  public DailyHealthSummaryResponse upsert(Integer userId, DailyHealthSummaryUpsertRequest req) {
    DailyHealthSummary entity =
        repo.findByUserIdAndDate(userId, req.date())
            .map(
                existing -> {
                  existing.updateSummary(
                      req.steps(), req.caloriesBurned(), req.sleepMinutes(), req.avgHeartRate());
                  return existing;
                })
            .orElseGet(
                () ->
                    repo.save(
                        DailyHealthSummary.builder()
                            .userId(userId)
                            .date(req.date())
                            .steps(req.steps())
                            .caloriesBurned(req.caloriesBurned())
                            .sleepMinutes(req.sleepMinutes())
                            .avgHeartRate(req.avgHeartRate())
                            .build()));
    return DailyHealthSummaryResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public List<DailyHealthSummaryResponse> findRange(Integer userId, LocalDate from, LocalDate to) {
    return repo.findByUserIdAndDateBetweenOrderByDateAsc(userId, from, to).stream()
        .map(DailyHealthSummaryResponse::from)
        .toList();
  }
}
