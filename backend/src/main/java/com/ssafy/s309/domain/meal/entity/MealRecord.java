package com.ssafy.s309.domain.meal.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "meal_records")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MealRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "food_id")
  private Long foodId;

  @Column(name = "image_origin_name", length = 255)
  private String imageOriginName;

  @Column(name = "image_storage_key", length = 255)
  private String imageStorageKey;

  @Column(name = "is_processed", nullable = false)
  private Boolean isProcessed;

  @Column(name = "recorded_at", nullable = false)
  private LocalDateTime recordedAt;
}
