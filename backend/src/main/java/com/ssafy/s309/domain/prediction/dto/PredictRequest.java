package com.ssafy.s309.domain.prediction.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PredictRequest(
    Integer foodId,
    @NotBlank String foodName,
    @NotNull
        @DecimalMin(value = "0.0", message = "탄수화물은 0g 이상이어야 합니다")
        @DecimalMax(value = "1000.00", message = "탄수화물은 1000g 이하여야 합니다")
        BigDecimal carbsG,
    @NotNull
        @DecimalMin(value = "0.0", message = "단백질은 0g 이상이어야 합니다")
        @DecimalMax(value = "1000.00", message = "단백질은 1000g 이하여야 합니다")
        BigDecimal proteinG,
    @NotNull
        @DecimalMin(value = "0.0", message = "지방은 0g 이상이어야 합니다")
        @DecimalMax(value = "1000.00", message = "지방은 1000g 이하여야 합니다")
        BigDecimal fatG,
    @DecimalMin(value = "0.0", message = "식이섬유는 0g 이상이어야 합니다")
        @DecimalMax(value = "100.00", message = "식이섬유는 100g 이하여야 합니다")
        BigDecimal fiberG,
    @NotNull
        @DecimalMin(value = "0.0", message = "칼로리는 0kcal 이상이어야 합니다")
        @DecimalMax(value = "10000.00", message = "칼로리는 10000kcal 이하여야 합니다")
        BigDecimal kcal,
    @DecimalMin(value = "0.0", message = "당류는 0g 이상이어야 합니다")
        @DecimalMax(value = "1000.00", message = "당류는 1000g 이하여야 합니다")
        BigDecimal sugarG,
    Integer giScore) {}
