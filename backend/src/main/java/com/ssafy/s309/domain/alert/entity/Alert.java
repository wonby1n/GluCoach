package com.ssafy.s309.domain.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "alerts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Alert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "alert_type", nullable = false, length = 50)
  private String alertType;

  @Column(name = "glucose_record_id")
  private Long glucoseRecordId;

  @Column(name = "weekly_report_id")
  private Long weeklyReportId;

  @Column(name = "message", length = 500)
  private String message;

  @Column(name = "source", nullable = false, length = 20)
  private String source;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "is_read", nullable = false)
  private Boolean isRead;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public void markRead() {
    this.isRead = true;
    if (this.resolvedAt == null) {
      this.resolvedAt = LocalDateTime.now();
    }
  }

  public void resolve() {
    this.resolvedAt = LocalDateTime.now();
  }
}
