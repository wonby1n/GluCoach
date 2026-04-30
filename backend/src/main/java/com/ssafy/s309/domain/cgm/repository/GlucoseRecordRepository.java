package com.ssafy.s309.domain.cgm.repository;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlucoseRecordRepository extends JpaRepository<GlucoseRecord, Long> {

  List<GlucoseRecord> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
      Long userId, LocalDateTime from, LocalDateTime to);
}
