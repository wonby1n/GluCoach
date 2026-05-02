package com.ssafy.s309.domain.health.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;

public record StepRecordBatchRequest(@NotEmpty @Valid List<Item> items) {

  public record Item(
      @NotNull LocalDateTime recordedAt, @NotNull @PositiveOrZero Integer stepsTotal) {}
}
