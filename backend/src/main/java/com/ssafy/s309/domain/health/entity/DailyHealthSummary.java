package com.ssafy.s309.domain.health.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "daily_health_summaries")
@IdClass(DailyHealthSummary.PK.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailyHealthSummary {

  @Id
  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Id
  @Column(name = "date", nullable = false)
  private LocalDate date;

  @Column(name = "steps")
  private Integer steps;

  @Column(name = "calories_burned", precision = 6, scale = 2)
  private BigDecimal caloriesBurned;

  @Column(name = "sleep_start")
  private LocalDateTime sleepStart;

  @Column(name = "sleep_end")
  private LocalDateTime sleepEnd;

  @Column(name = "sleep_minutes")
  private Integer sleepMinutes;

  @Column(name = "avg_heart_rate", precision = 5, scale = 1)
  private BigDecimal avgHeartRate;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public void updateSummary(
      Integer steps,
      BigDecimal caloriesBurned,
      LocalDateTime sleepStart,
      LocalDateTime sleepEnd,
      Integer sleepMinutes,
      BigDecimal avgHeartRate) {
    if (steps != null) this.steps = steps;
    if (caloriesBurned != null) this.caloriesBurned = caloriesBurned;
    if (sleepStart != null) this.sleepStart = sleepStart;
    if (sleepEnd != null) this.sleepEnd = sleepEnd;
    if (sleepMinutes != null) this.sleepMinutes = sleepMinutes;
    if (avgHeartRate != null) this.avgHeartRate = avgHeartRate;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class PK implements Serializable {
    private Integer userId;
    private LocalDate date;
  }
}
