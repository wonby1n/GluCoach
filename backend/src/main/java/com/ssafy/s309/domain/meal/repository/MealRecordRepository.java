package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRecordRepository extends JpaRepository<MealRecord, Long> {

  List<MealRecord> findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
      Long userId, LocalDateTime from, LocalDateTime to);
}
