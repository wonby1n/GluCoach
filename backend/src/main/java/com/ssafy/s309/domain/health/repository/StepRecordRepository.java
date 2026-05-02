package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.StepRecord;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StepRecordRepository extends JpaRepository<StepRecord, Long> {

  /**
   * 시간 윈도우 내 걸음수 증분 (counter-rate 패턴).
   *
   * <p>{@code steps_total}은 누적값이므로 윈도우의 (최댓값 - 최솟값)이 그 구간 동안 걸은 걸음수다. 데이터 0건이면 NULL이 아닌 0을 반환하도록
   * COALESCE 처리.
   */
  @Query(
      value =
          """
          SELECT COALESCE(MAX(steps_total) - MIN(steps_total), 0)
            FROM step_records
           WHERE user_id = :userId
             AND recorded_at BETWEEN :start AND :end
          """,
      nativeQuery = true)
  int sumStepsInWindow(
      @Param("userId") Integer userId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
