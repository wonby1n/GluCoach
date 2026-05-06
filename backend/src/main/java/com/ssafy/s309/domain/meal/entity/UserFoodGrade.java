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
@Table(name = "user_food_grades")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFoodGrade extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "food_id", nullable = false)
  private Integer foodId;

  @Column(name = "avg_slope", nullable = false, precision = 3, scale = 1)
  private BigDecimal avgSlope;

  @Column(name = "grade", nullable = false, length = 1)
  private String grade;

  @Column(name = "meal_count", nullable = false)
  private Integer mealCount;

  public void update(BigDecimal newAvgSlope, String newGrade, int newMealCount) {
    this.avgSlope = newAvgSlope;
    this.grade = newGrade;
    this.mealCount = newMealCount;
  }
}
