package com.ssafy.s309.domain.prediction.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predict")
@RequiredArgsConstructor
@Tag(name = "Prediction", description = "식전 혈당 예측 API")
public class PredictionController {

  private final PredictionService predictionService;

  @Operation(summary = "단일 음식 식전 혈당 예측")
  @PostMapping("/glucose")
  public ResponseEntity<PredictResponse> predict(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestBody @Valid PredictRequest request) {
    return ResponseEntity.ok(predictionService.predict(principal.userId(), request));
  }

  @Operation(summary = "A/B 음식 비교 식전 혈당 예측 (병렬 호출)")
  @PostMapping("/glucose/compare")
  public ResponseEntity<AbPredictResponse> comparePredict(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestBody @Valid AbPredictRequest request) {
    return ResponseEntity.ok(predictionService.comparePredict(principal.userId(), request));
  }
}
