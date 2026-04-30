package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.SleepRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SleepRecordRepository extends JpaRepository<SleepRecord, Long> {

  @Query(
      """
      SELECT s FROM SleepRecord s
       WHERE s.userId = :userId
         AND s.startedAt <= :to
         AND s.endedAt   >= :from
       ORDER BY s.startedAt ASC
      """)
  List<SleepRecord> findOverlappingByUserId(
      @Param("userId") Long userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
