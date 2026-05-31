package com.ssafy.s309.domain.prediction.service;

import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.service.FoodResolutionService;
import com.ssafy.s309.domain.prediction.client.FoodDetectClient;
import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import com.ssafy.s309.domain.prediction.client.dto.FoodDetection;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse.Status;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진 통합 식전 예측 오케스트레이터.
 *
 * <p>흐름: ① CV 인식 → ② 신뢰도 게이트 → ③ foods 해석 → ④ 예측 모델 호출. 신뢰도 미달이면 ②에서, foods 영양정보 결측이면 ③에서 조기 반환한다.
 *
 * <p>각 sub-step (FoodResolutionService.resolve / PredictionService.predictForFood) 이 자체 트랜잭션을 가지므로
 * 본 서비스는 {@code @Transactional} 을 갖지 않는다 — 외부 HTTP 호출(CV) 동안 DB 커넥션 점유 회피.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FromImagePredictionService {

  /** AI 측 confidence 가 이 값 미만이면 사용자 확인 UI 로 폴백 (스펙 상 0.5). */
  static final double CONFIDENCE_THRESHOLD = 0.5;

  private final FoodDetectClient foodDetectClient;
  private final FoodResolutionService foodResolutionService;
  private final PredictionService predictionService;

  public FromImagePredictResponse predict(Integer userId, MultipartFile image) {
    FoodDetectResponse detection = foodDetectClient.detect(image);
    List<FoodDetection> detections = detection.detections();

    // detections 는 AI 측에서 confidence DESC 정렬됨 (FoodDetectResponse javadoc 참조).
    if (detections.isEmpty() || detections.get(0).confidence() < CONFIDENCE_THRESHOLD) {
      String topNameKo = detections.isEmpty() ? null : detections.get(0).nameKo();
      String topNameEn = detections.isEmpty() ? null : detections.get(0).nameEn();
      Double topConfidence = detections.isEmpty() ? null : detections.get(0).confidence();
      log.info(
          "[FromImagePredict] LOW_CONFIDENCE userId={} count={} topKo={} topEn={} topConfidence={}",
          userId,
          detections.size(),
          topNameKo,
          topNameEn,
          topConfidence);
      return new FromImagePredictResponse(
          Status.LOW_CONFIDENCE, detections, null, null, null, true);
    }

    // 고신뢰지만 nameKo 누락 — AI FOOD_LABELS 에 ko 라벨 없는 신규 클래스가 추가됐다는 뜻 = AI 스펙 위반.
    // 운영 alert 가 필요하므로 WARN. 사용자 UX 는 LOW_CONFIDENCE 와 동일하게 폴백.
    if (isBlank(detections.get(0).nameKo())) {
      FoodDetection top = detections.get(0);
      log.warn(
          "[FromImagePredict] AI nameKo 누락 — LOW_CONFIDENCE 폴백. userId={} topEn={} topConfidence={}",
          userId,
          top.nameEn(),
          top.confidence());
      return new FromImagePredictResponse(
          Status.LOW_CONFIDENCE, detections, null, null, null, true);
    }

    FoodDetection top = detections.get(0);
    FoodResolution resolution = foodResolutionService.resolve(top.nameKo());
    Food food = resolution.food();

    if (resolution.status() == ResolutionStatus.PENDING_NUTRITION) {
      log.info(
          "[FromImagePredict] PENDING_NUTRITION userId={} foodId={} name={}",
          userId,
          food.getId(),
          food.getName());
      return new FromImagePredictResponse(
          Status.PENDING_NUTRITION, detections, food.getId(), food.getName(), null, false);
    }

    PredictResponse prediction = predictionService.predictForFood(userId, food);
    log.info(
        "[FromImagePredict] OK userId={} foodId={} resolution={}",
        userId,
        food.getId(),
        resolution.status());
    return new FromImagePredictResponse(
        Status.OK, detections, food.getId(), food.getName(), prediction, false);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
