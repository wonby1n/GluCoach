package com.ssafy.s309.domain.alert.repository;

import com.ssafy.s309.domain.alert.entity.Alert;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

  /** 30분 dedup 윈도우 검사: 같은 user/alert_type, 미해결, 최근 30분 내 alert이 있는지. */
  boolean existsByUserIdAndAlertTypeAndResolvedAtIsNullAndCreatedAtAfter(
      Integer userId, String alertType, LocalDateTime since);

  /** Agent #6 notification_history: 최근 N시간 알림. soft-delete 제외, 최신순. */
  List<Alert> findByUserIdAndDeletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
      Integer userId, LocalDateTime since);
}
