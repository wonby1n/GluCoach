package com.ssafy.s309.domain.sleep.repository;

import com.ssafy.s309.domain.sleep.entity.SleepSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepSessionRepository extends JpaRepository<SleepSession, Integer> {

  Optional<SleepSession> findByUserIdAndStartedAt(Integer userId, LocalDateTime startedAt);
}
