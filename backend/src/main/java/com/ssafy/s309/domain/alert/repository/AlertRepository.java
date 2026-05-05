package com.ssafy.s309.domain.alert.repository;

import com.ssafy.s309.domain.alert.entity.Alert;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

  /** 30분 dedup 윈도우 검사: 같은 user/alert_type, 미해결, 최근 30분 내 alert이 있는지. */
  boolean existsByUserIdAndAlertTypeAndResolvedAtIsNullAndCreatedAtAfter(
      Integer userId, String alertType, LocalDateTime since);

  /** Agent #6 notification_history: 최근 N시간 알림. soft-delete 제외, 최신순. */
  List<Alert> findByUserIdAndDeletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
      Integer userId, LocalDateTime since);

  /** 정상 복귀 시 종결 대상 조회: 같은 user, 해당 alert_type 중 미해결 건. */
  List<Alert> findByUserIdAndAlertTypeInAndResolvedAtIsNull(
      Integer userId, Collection<String> alertTypes);

  /** 사용자 알림 목록 (soft-delete 제외, is_read 옵션 필터, 최신순 페이징). */
  @Query(
      "SELECT a FROM Alert a WHERE a.userId = :userId AND a.deletedAt IS NULL"
          + " AND (:isRead IS NULL OR a.isRead = :isRead) ORDER BY a.createdAt DESC")
  Page<Alert> findAlerts(
      @Param("userId") Integer userId, @Param("isRead") Boolean isRead, Pageable pageable);

  /** 읽지 않은 알림 수 (soft-delete 제외). */
  long countByUserIdAndIsReadFalseAndDeletedAtIsNull(Integer userId);

  /** IDOR 방지 단건 조회 — userId가 일치해야 반환. */
  Optional<Alert> findByIdAndUserId(Integer id, Integer userId);
}
