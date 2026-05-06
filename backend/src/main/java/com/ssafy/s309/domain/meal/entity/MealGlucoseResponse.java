package com.ssafy.s309.domain.meal.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meal_glucose_responses")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealGlucoseResponse extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "meal_id", nullable = false, unique = true)
  private Integer mealId;

  @Column(name = "baseline_glucose_id", nullable = false)
  private Long baselineGlucoseId;

  @Column(name = "peak_glucose_id", nullable = false)
  private Long peakGlucoseId;

  @Column(name = "slope", nullable = false, precision = 3, scale = 1)
  private BigDecimal slope;
}
