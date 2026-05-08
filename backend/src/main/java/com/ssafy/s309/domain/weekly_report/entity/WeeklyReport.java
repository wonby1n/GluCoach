package com.ssafy.s309.domain.weekly_report.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weekly_reports")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WeeklyReport extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "week_start")
  private LocalDate weekStart;

  @Column(name = "avg_glucose", precision = 5, scale = 2)
  private BigDecimal avgGlucose;

  @Column(name = "min_glucose", precision = 5, scale = 2)
  private BigDecimal minGlucose;

  @Column(name = "max_glucose", precision = 5, scale = 2)
  private BigDecimal maxGlucose;

  @Column(name = "glucose_sd", precision = 5, scale = 2)
  private BigDecimal glucoseSd;

  @Column(name = "time_in_range", precision = 5, scale = 2)
  private BigDecimal timeInRange;

  @Column(name = "time_above_range", precision = 5, scale = 2)
  private BigDecimal timeAboveRange;

  @Column(name = "time_below_range", precision = 5, scale = 2)
  private BigDecimal timeBelowRange;

  @Column(name = "ai_summary", columnDefinition = "TEXT")
  private String aiSummary;

  @Column(name = "ai_suggest", columnDefinition = "TEXT")
  private String aiSuggest;

  @Column(name = "pdf_key", nullable = false, length = 100)
  private String pdfKey;
}
