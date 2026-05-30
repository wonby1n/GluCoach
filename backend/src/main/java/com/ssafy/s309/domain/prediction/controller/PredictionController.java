package com.ssafy.s309.domain.prediction.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.prediction.client.GlucosePersonalizeClient;
import com.ssafy.s309.domain.prediction.client.dto.PersonalizeRequest;
import com.ssafy.s309.domain.prediction.client.dto.PersonalizeResponse;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.service.FromImagePredictionService;
import com.ssafy.s309.domain.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/predict")
@RequiredArgsConstructor
@Tag(name = "Prediction", description = "식전 혈당 예측 API")
public class PredictionController {

  private final PredictionService predictionService;
  private final FromImagePredictionService fromImagePredictionService;
  private final GlucosePersonalizeClient glucosePersonalizeClient;

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

  @Operation(
      summary = "사진 통합 식전 혈당 예측",
      description = "이미지 1장 업로드 → CV 인식 + foods 조회 + 예측 모델 호출까지 단일 호출로 처리")
  @PostMapping(value = "/glucose/from-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FromImagePredictResponse> predictFromImage(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestPart("image") MultipartFile image) {
    if (image.isEmpty()
        || image.getContentType() == null
        || !image.getContentType().startsWith("image/")) {
      throw new IllegalArgumentException("이미지 파일이 필요합니다");
    }
    return ResponseEntity.ok(fromImagePredictionService.predict(principal.userId(), image));
  }

  @Operation(
      summary = "혈당 예측 모델 개인화 fine-tune",
      description =
          "사용자 실측 식사+혈당 이력으로 LSTM 모델을 fine-tune. AI 서버로 forward. "
              + "응답 시간이 길 수 있음(최대 10분). 데모/시연용.")
  @PostMapping("/glucose/personalize")
  public ResponseEntity<PersonalizeResponse> personalize(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestBody @Valid PersonalizeRequest request) {
    PersonalizeRequest forwarded =
        new PersonalizeRequest(
            String.valueOf(principal.userId()), request.userProfile(), request.history());
    return ResponseEntity.ok(glucosePersonalizeClient.personalize(forwarded));
  }
}
