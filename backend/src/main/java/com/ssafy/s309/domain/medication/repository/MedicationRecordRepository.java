package com.ssafy.s309.domain.medication.repository;

import com.ssafy.s309.domain.medication.entity.MedicationRecord;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRecordRepository extends JpaRepository<MedicationRecord, Integer> {

  @Query(
      "SELECT COUNT(m) FROM MedicationRecord m"
          + " WHERE m.userId = :userId"
          + " AND m.takenAt >= :from AND m.takenAt < :to")
  long countByUserIdAndTakenAtBetween(
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
