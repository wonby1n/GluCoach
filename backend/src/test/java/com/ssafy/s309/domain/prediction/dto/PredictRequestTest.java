package com.ssafy.s309.domain.prediction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class PredictRequestTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void init() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void cleanup() {
    factory.close();
  }

  @Test
  void 정상값_검증_통과() {
    PredictRequest request =
        new PredictRequest(
            1,
            "현미밥",
            new BigDecimal("35.50"),
            new BigDecimal("4.20"),
            new BigDecimal("0.80"),
            new BigDecimal("2.00"),
            new BigDecimal("180.00"),
            new BigDecimal("0.30"),
            65);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  void sugarG_null_허용() {
    PredictRequest request =
        new PredictRequest(
            1,
            "현미밥",
            new BigDecimal("35.50"),
            new BigDecimal("4.20"),
            new BigDecimal("0.80"),
            null,
            new BigDecimal("180.00"),
            null,
            65);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  void 필수_영양소_null_거부() {
    PredictRequest request =
        new PredictRequest(null, "음식", null, null, null, null, null, null, null);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations)
        .extracting(v -> v.getPropertyPath().toString())
        .contains("carbsG", "proteinG", "fatG", "kcal");
  }

  @Test
  void 음수_탄수화물_거부() {
    PredictRequest request =
        new PredictRequest(
            1,
            "음식",
            new BigDecimal("-1.00"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            null,
            new BigDecimal("100.00"),
            new BigDecimal("10.00"),
            50);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).hasSize(1);
    ConstraintViolation<PredictRequest> violation = violations.iterator().next();
    assertThat(violation.getPropertyPath().toString()).isEqualTo("carbsG");
    assertThat(violation.getMessage()).contains("0g 이상");
  }

  @Test
  void 탄수화물_상한_초과_거부() {
    PredictRequest request =
        new PredictRequest(
            1,
            "음식",
            new BigDecimal("1000.01"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            null,
            new BigDecimal("100.00"),
            new BigDecimal("10.00"),
            50);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("carbsG");
  }

  @Test
  void 칼로리_상한_초과_거부() {
    PredictRequest request =
        new PredictRequest(
            1,
            "음식",
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            null,
            new BigDecimal("10000.01"),
            new BigDecimal("10.00"),
            50);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("kcal");
  }

  @Test
  void foodName_공백_거부() {
    PredictRequest request =
        new PredictRequest(
            1,
            "  ",
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            null,
            new BigDecimal("100.00"),
            new BigDecimal("10.00"),
            50);

    Set<ConstraintViolation<PredictRequest>> violations = validator.validate(request);

    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("foodName");
  }
}
