package com.ssafy.s309.domain.alert.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SosRequest(@NotNull BigDecimal latitude, @NotNull BigDecimal longitude) {}
