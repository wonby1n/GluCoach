package com.ssafy.s309.domain.cgm.repository;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.weekly_report.dto.projection.DailyGlucoseProjection;
import com.ssafy.s309.domain.weekly_report.dto.projection.HourlyGlucoseProjection;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyGlucoseStatsProjection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlucoseRecordRepository extends JpaRepository<GlucoseRecord, Long> {

  List<GlucoseRecord> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
      Integer userId, LocalDateTime from, LocalDateTime to);

  @Query(
      value =
          """
          SELECT
            ROUND(AVG(value)::numeric, 2)           AS avg_glucose,
            ROUND(MIN(value)::numeric, 2)           AS min_glucose,
            ROUND(MAX(value)::numeric, 2)           AS max_glucose,
            ROUND(COALESCE(STDDEV_POP(value), 0)::numeric, 2) AS glucose_sd,
            ROUND(100.0 * COUNT(*) FILTER (WHERE value > :targetHigh)
              / NULLIF(COUNT(*), 0), 2)             AS time_above_range,
            ROUND(100.0 * COUNT(*) FILTER (WHERE value < :targetLow)
              / NULLIF(COUNT(*), 0), 2)             AS time_below_range
          FROM glucose_records
          WHERE user_id = :userId
            AND measured_at >= :from
            AND measured_at < :to
          """,
      nativeQuery = true)
  Optional<WeeklyGlucoseStatsProjection> findWeeklyGlucoseStats(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("targetLow") BigDecimal targetLow,
      @Param("targetHigh") BigDecimal targetHigh);

  @Query(
      value =
          """
          SELECT
            EXTRACT(HOUR FROM measured_at)::int AS hour,
            ROUND(AVG(value)::numeric, 2)       AS avg
          FROM glucose_records
          WHERE user_id = :userId
            AND measured_at >= :from
            AND measured_at < :to
          GROUP BY EXTRACT(HOUR FROM measured_at)
          ORDER BY hour
          """,
      nativeQuery = true)
  List<HourlyGlucoseProjection> findHourlyGlucosePattern(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      value =
          """
          SELECT
            DATE(measured_at)                   AS date,
            ROUND(AVG(value)::numeric, 2)       AS avg,
            ROUND(MIN(value)::numeric, 2)       AS min,
            ROUND(MAX(value)::numeric, 2)       AS max
          FROM glucose_records
          WHERE user_id = :userId
            AND measured_at >= :from
            AND measured_at < :to
          GROUP BY DATE(measured_at)
          ORDER BY date
          """,
      nativeQuery = true)
  List<DailyGlucoseProjection> findDailyGlucoseTrend(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
