package com.ssafy.s309.domain.timeline.dto;

import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.time.LocalDateTime;

public record MealPin(Long id, Long foodId, LocalDateTime recordedAt, String imageStorageKey) {

  public static MealPin from(MealRecord r) {
    return new MealPin(r.getId(), r.getFoodId(), r.getRecordedAt(), r.getImageStorageKey());
  }
}
