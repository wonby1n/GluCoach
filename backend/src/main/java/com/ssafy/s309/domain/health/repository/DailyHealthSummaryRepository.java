package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.DailyHealthSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyHealthSummaryRepository
    extends JpaRepository<DailyHealthSummary, DailyHealthSummary.PK> {

  Optional<DailyHealthSummary> findByUserIdAndDate(Integer userId, LocalDate date);

  List<DailyHealthSummary> findByUserIdAndDateBetweenOrderByDateAsc(
      Integer userId, LocalDate from, LocalDate to);
}
