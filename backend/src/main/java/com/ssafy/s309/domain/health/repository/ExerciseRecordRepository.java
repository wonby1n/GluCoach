package com.ssafy.s309.domain.health.repository;

import com.ssafy.s309.domain.health.entity.ExerciseRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRecordRepository extends JpaRepository<ExerciseRecord, Integer> {

  @Query(
      """
      SELECT e FROM ExerciseRecord e
       WHERE e.userId = :userId
         AND e.startedAt <= :to
         AND e.endedAt   >= :from
       ORDER BY e.startedAt ASC
      """)
  List<ExerciseRecord> findOverlappingByUserId(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
