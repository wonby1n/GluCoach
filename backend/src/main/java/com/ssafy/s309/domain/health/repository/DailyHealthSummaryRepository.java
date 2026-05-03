package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.DailyHealthSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyHealthSummaryRepository
    extends JpaRepository<DailyHealthSummary, DailyHealthSummary.PK> {

  Optional<DailyHealthSummary> findByUserIdAndDate(Integer userId, LocalDate date);

  List<DailyHealthSummary> findByUserIdAndDateBetweenOrderByDateAsc(
      Integer userId, LocalDate from, LocalDate to);

  /**
   * 최근 7일(:date 포함, [date-6, date]) 수면 분 평균. sleep_minutes IS NULL 행은 평균에서 제외 (AVG가 자동 무시). 7일 모두
   * NULL/없음 → null 반환 — Service에서 0으로 처리.
   */
  @Query(
      value =
          """
          SELECT AVG(sleep_minutes)
          FROM daily_health_summaries
          WHERE user_id = :userId
            AND date BETWEEN (CAST(:date AS DATE) - INTERVAL '6 day') AND :date
            AND sleep_minutes IS NOT NULL
          """,
      nativeQuery = true)
  BigDecimal findAverageSleepMinutesLast7Days(
      @Param("userId") Integer userId, @Param("date") LocalDate date);
}
