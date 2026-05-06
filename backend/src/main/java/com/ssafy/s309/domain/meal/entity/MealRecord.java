package com.ssafy.s309.domain.meal.entity;

import com.ssafy.s309.common.entity.BaseEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meal_records")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "food_id")
  private Integer foodId;

  /**
   * Food 객체 그래프 — readonly join (insertable/updatable=false). foodId가 INSERT/UPDATE 권한을 가지므로 충돌 없이
   * 양립. Agent #4 meals JPQL에서 m.food.name 등 dot 접근 가능.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "food_id", insertable = false, updatable = false)
  private Food food;

  @Column(name = "image_origin_name", length = 255)
  private String imageOriginName;

  @Column(name = "image_storage_key", length = 255)
  private String imageStorageKey;

  @Column(name = "is_processed", nullable = false)
  private Boolean isProcessed;

  @Column(name = "memo", length = 255)
  private String memo;

  @Column(name = "recorded_at", nullable = false)
  private LocalDateTime recordedAt;

  public void markAsProcessed() {
    this.isProcessed = true;
  }
}
