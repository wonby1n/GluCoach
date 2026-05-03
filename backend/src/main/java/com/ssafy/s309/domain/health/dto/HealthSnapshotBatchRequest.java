package com.ssafy.s309.domain.health.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record HealthSnapshotBatchRequest(@NotEmpty @Valid List<Item> items) {

  /**
   * 한 시점의 모든 메트릭 스냅샷. recordedAt만 필수, 나머지 메트릭은 SDK가 일부만 반환할 수 있어 모두 nullable.
   *
   * <p>heart_rate는 DB CHECK (0~300)와 정렬해 Bean Validation에서 미리 걸러 400 응답.
   */
  public record Item(
      @NotNull LocalDateTime recordedAt,
      @PositiveOrZero Integer stepsTotal,
      @PositiveOrZero BigDecimal caloriesBurned,
      @PositiveOrZero @DecimalMax("300.0") BigDecimal heartRate) {}
}
