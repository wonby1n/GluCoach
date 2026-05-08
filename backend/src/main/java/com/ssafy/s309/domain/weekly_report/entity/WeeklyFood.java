package com.ssafy.s309.domain.weekly_report.entity;

import com.ssafy.s309.domain.food.entity.Food;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "weekly_foods")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WeeklyFood {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "report_id", nullable = false)
  private Integer reportId;

  @Column(name = "food_id", nullable = false)
  private Integer foodId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "food_id", insertable = false, updatable = false)
  private Food food;

  @Column(name = "type", nullable = false, length = 4)
  private String type;

  @Column(name = "image_storage_key", length = 255)
  private String imageStorageKey;

  @Column(name = "avg_slope", nullable = false, precision = 3, scale = 1)
  private BigDecimal avgSlope;
}
