package com.ssafy.s309.domain.prediction.controller;

import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}")
@RequiredArgsConstructor
@Tag(name = "Prediction", description = "식전 혈당 예측 API")
public class PredictionController {

  private final PredictionService predictionService;

  @Operation(summary = "단일 음식 식전 혈당 예측")
  @PostMapping("/predict/glucose")
  public ResponseEntity<PredictResponse> predict(
      @PathVariable Long userId, @RequestBody @Valid PredictRequest request) {
    return ResponseEntity.ok(predictionService.predict(userId, request));
  }

  @Operation(summary = "A/B 음식 비교 식전 혈당 예측 (병렬 호출)")
  @PostMapping("/predict/glucose/compare")
  public ResponseEntity<AbPredictResponse> comparePredict(
      @PathVariable Long userId, @RequestBody @Valid AbPredictRequest request) {
    return ResponseEntity.ok(predictionService.comparePredict(userId, request));
  }
}
