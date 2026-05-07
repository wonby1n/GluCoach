package com.ssafy.s309.domain.prediction.service;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.MealInfo;
import com.ssafy.s309.domain.prediction.client.dto.UserProfileWithPattern;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.CurvePoint;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.prediction.repository.GlucosePredictionRepository;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionService {

  // CGM 미구현 환경 fallback. 향후 최근 CGM 측정값 시계열로 교체.
  private static final double DEFAULT_FASTING_BG = 100.0;
  private static final double DEFAULT_WEIGHT_KG = 60.0;
  private static final String DEFAULT_ACTIVITY = "medium";
  private static final String DEFAULT_MEAL_PATTERN = "regular_3";

  // AI 측 _parse_iso_to_hour 가 naive datetime 을 KST 로 해석하므로 명시적으로 KST 로 생성.
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final GlucosePredictClient glucosePredictClient;
  private final GlucosePredictionRepository predictionRepository;
  private final UserRepository userRepository;

  @Transactional
  public PredictResponse predict(Integer userId, PredictRequest request) {
    User user = findUser(userId);
    GlucosePredictResponse aiResponse = glucosePredictClient.predict(buildAiRequest(user, request));
    GlucosePrediction saved = savePrediction(user, request, aiResponse);
    return toResponse(saved.getId(), aiResponse);
  }

  /**
   * Food 엔티티 직접 입력 변종 — 사진 통합 예측({@code /from-image}) 흐름에서 사용.
   *
   * <p>호출자(orchestrator)는 {@code food.carbsG NOT NULL} 보장 시점에만 호출해야 한다 (PENDING_NUTRITION 분기는 별도).
   * protein/fat/kcal 결측은 0 으로 폴백 — AI 모델은 carbs 만 사용하므로 예측 정확도에 영향 없고, glucose_predictions 테이블에 0
   * 으로 기록될 뿐이다.
   */
  @Transactional
  public PredictResponse predictForFood(Integer userId, Food food) {
    PredictRequest request =
        new PredictRequest(
            food.getId(),
            food.getName(),
            food.getCarbsG(),
            food.getProteinG() != null ? food.getProteinG() : BigDecimal.ZERO,
            food.getFatG() != null ? food.getFatG() : BigDecimal.ZERO,
            food.getKcal() != null ? food.getKcal() : BigDecimal.ZERO,
            food.getSugarG(),
            null);
    return predict(userId, request);
  }

  public AbPredictResponse comparePredict(Integer userId, AbPredictRequest request) {
    User user = findUser(userId);

    CompletableFuture<PredictResponse> futureA =
        CompletableFuture.supplyAsync(
            () -> {
              GlucosePredictResponse r =
                  glucosePredictClient.predict(buildAiRequest(user, request.foodA()));
              GlucosePrediction saved = savePrediction(user, request.foodA(), r);
              return toResponse(saved.getId(), r);
            });

    CompletableFuture<PredictResponse> futureB =
        CompletableFuture.supplyAsync(
            () -> {
              GlucosePredictResponse r =
                  glucosePredictClient.predict(buildAiRequest(user, request.foodB()));
              GlucosePrediction saved = savePrediction(user, request.foodB(), r);
              return toResponse(saved.getId(), r);
            });

    return new AbPredictResponse(futureA.join(), futureB.join());
  }

  private User findUser(Integer userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저: " + userId));
  }

  private GlucosePredictRequest buildAiRequest(User user, PredictRequest request) {
    MealInfo meal = new MealInfo(request.carbsG().doubleValue(), LocalDateTime.now(KST).toString());

    double weightKg = user.getWeight() != null ? user.getWeight().doubleValue() : DEFAULT_WEIGHT_KG;

    UserProfileWithPattern profile =
        new UserProfileWithPattern(
            DEFAULT_FASTING_BG,
            weightKg,
            DEFAULT_ACTIVITY,
            mapDiabetesType(user.getDiabetesType()),
            DEFAULT_MEAL_PATTERN);

    // CGM 미구현. fasting_bg 와 동일 값 단일 원소로 fallback (AI min_length=1 통과 + 모델은
    // 마지막 값을 baseline 으로 사용). 향후 최근 60분 5분 간격 시계열로 교체 예정.
    return new GlucosePredictRequest(
        String.valueOf(user.getId()), List.of(DEFAULT_FASTING_BG), meal, profile);
  }

  // AI 측 Literal["T1D", "T2D", "Normal"] 과 일치시키기 위해 Title Case 변환.
  private String mapDiabetesType(DiabetesType type) {
    if (type == null) return "Normal";
    return switch (type) {
      case NORMAL -> "Normal";
      case T1D -> "T1D";
      case T2D -> "T2D";
    };
  }

  private GlucosePrediction savePrediction(
      User user, PredictRequest request, GlucosePredictResponse aiResponse) {
    return predictionRepository.save(
        GlucosePrediction.builder()
            .user(user)
            .foodId(request.foodId())
            .foodName(request.foodName())
            .predictedCurve(aiResponse.curve())
            .predictedPeak(
                aiResponse.peakMgdl() != null ? BigDecimal.valueOf(aiResponse.peakMgdl()) : null)
            .build());
  }

  private PredictResponse toResponse(Integer predictionId, GlucosePredictResponse aiResponse) {
    List<CurvePoint> curve =
        aiResponse.curve().stream()
            .map(p -> new CurvePoint(p.minuteOffset(), p.glucoseMgdl()))
            .toList();
    return new PredictResponse(
        predictionId,
        curve,
        aiResponse.peakMgdl(),
        aiResponse.peakMinute(),
        aiResponse.confidence());
  }
}
