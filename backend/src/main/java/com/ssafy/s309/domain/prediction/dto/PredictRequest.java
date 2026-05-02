package com.ssafy.s309.domain.prediction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record PredictRequest(
    Integer foodId,
    @NotBlank String foodName,
    @DecimalMin(value = "0.00", inclusive = true, message = "탄수화물은 0g 이상이어야 합니다")
        @Digits(integer = 3, fraction = 2, message = "탄수화물은 999.99g 이하 (소수점 둘째 자리까지) 입력 가능합니다")
        BigDecimal carbsG,
    @DecimalMin(value = "0.00", inclusive = true, message = "단백질은 0g 이상이어야 합니다")
        @Digits(integer = 3, fraction = 2, message = "단백질은 999.99g 이하 (소수점 둘째 자리까지) 입력 가능합니다")
        BigDecimal proteinG,
    @DecimalMin(value = "0.00", inclusive = true, message = "지방은 0g 이상이어야 합니다")
        @Digits(integer = 3, fraction = 2, message = "지방은 999.99g 이하 (소수점 둘째 자리까지) 입력 가능합니다")
        BigDecimal fatG,
    @DecimalMin(value = "0.00", inclusive = true, message = "칼로리는 0kcal 이상이어야 합니다")
        @Digits(integer = 4, fraction = 2, message = "칼로리는 9999.99kcal 이하 (소수점 둘째 자리까지) 입력 가능합니다")
        BigDecimal kcal,
    @DecimalMin(value = "0.00", inclusive = true, message = "당류는 0g 이상이어야 합니다")
        @Digits(integer = 3, fraction = 2, message = "당류는 999.99g 이하 (소수점 둘째 자리까지) 입력 가능합니다")
        BigDecimal sugarG,
    Integer giScore) {}
