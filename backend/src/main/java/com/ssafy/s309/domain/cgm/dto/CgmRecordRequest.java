package com.ssafy.s309.domain.cgm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CgmRecordRequest(
    @NotNull @DecimalMin("20") @DecimalMax("600") BigDecimal value,
    @NotNull LocalDateTime measuredAt) {}
