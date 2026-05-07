package com.ssafy.s309.domain.cgm.repository;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlucoseRecordRepository extends JpaRepository<GlucoseRecord, Long> {

  List<GlucoseRecord> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
      Integer userId, LocalDateTime from, LocalDateTime to);

  Optional<GlucoseRecord> findFirstByUserIdOrderByMeasuredAtDesc(Integer userId);
}
