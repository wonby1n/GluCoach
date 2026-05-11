package com.ssafy.s309.domain.food.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "foods")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Food extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "food_api_id", unique = true, length = 64)
  private String foodApiId;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  // 사용자 노출용 표시명. NULL 이면 FE 가 name 으로 fallback.
  // 식약처 API 매칭은 name 기준이므로 raw 보존, 표시만 분리.
  @Column(name = "display_name", length = 100)
  private String displayName;

  @Column(name = "category", length = 50)
  private String category;

  @Column(name = "kcal", precision = 6, scale = 2)
  private BigDecimal kcal;

  @Column(name = "carbs_g", precision = 5, scale = 2)
  private BigDecimal carbsG;

  @Column(name = "sugar_g", precision = 5, scale = 2)
  private BigDecimal sugarG;

  @Column(name = "protein_g", precision = 5, scale = 2)
  private BigDecimal proteinG;

  @Column(name = "fat_g", precision = 5, scale = 2)
  private BigDecimal fatG;

  @Column(name = "fiber_g", precision = 5, scale = 2)
  private BigDecimal fiberG;

  @Column(name = "saturated_fat_g", precision = 5, scale = 2)
  private BigDecimal saturatedFatG;

  @Column(name = "trans_fat_g", precision = 5, scale = 2)
  private BigDecimal transFatG;

  @Column(name = "cholesterol_mg", precision = 7, scale = 2)
  private BigDecimal cholesterolMg;

  @Column(name = "sodium_mg", precision = 7, scale = 2)
  private BigDecimal sodiumMg;

  @Column(name = "serving_size", precision = 7, scale = 2)
  private BigDecimal servingSize;

  @Column(name = "is_customized", nullable = false)
  private boolean isCustomized;

  @Column(name = "search_count", nullable = false)
  private int searchCount;

  @Column(name = "cached_at", nullable = false)
  private LocalDateTime cachedAt;

  public void incrementSearchCount() {
    this.searchCount++;
  }

  public void refresh(
      String category,
      BigDecimal kcal,
      BigDecimal carbsG,
      BigDecimal sugarG,
      BigDecimal proteinG,
      BigDecimal fatG,
      BigDecimal fiberG,
      BigDecimal saturatedFatG,
      BigDecimal transFatG,
      BigDecimal cholesterolMg,
      BigDecimal sodiumMg) {
    this.category = category;
    this.kcal = kcal;
    this.carbsG = carbsG;
    this.sugarG = sugarG;
    this.proteinG = proteinG;
    this.fatG = fatG;
    this.fiberG = fiberG;
    this.saturatedFatG = saturatedFatG;
    this.transFatG = transFatG;
    this.cholesterolMg = cholesterolMg;
    this.sodiumMg = sodiumMg;
    this.cachedAt = LocalDateTime.now();
  }
}
