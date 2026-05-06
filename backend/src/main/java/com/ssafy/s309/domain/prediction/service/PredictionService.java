package com.ssafy.s309.domain.prediction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.MealInfo;
import com.ssafy.s309.domain.prediction.client.dto.UserProfileWithPattern;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.repository.GlucosePredictionRepository;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

  private final GlucosePredictClient glucosePredictClient;
  private final GlucosePredictionRepository predictionRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public PredictResponse predict(Integer userId, PredictRequest request) {
    User user = findUser(userId);
    GlucosePredictResponse aiResponse = glucosePredictClient.predict(buildAiRequest(user, request));
    GlucosePrediction saved = savePrediction(user, request, aiResponse);
    return toResponse(saved.getId(), aiResponse);
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
    MealInfo meal = new MealInfo(request.carbsG().doubleValue(), LocalDateTime.now().toString());

    double weightKg = user.getWeight() != null ? user.getWeight().doubleValue() : DEFAULT_WEIGHT_KG;

    UserProfileWithPattern profile =
        new UserProfileWithPattern(
            DEFAULT_FASTING_BG,
            weightKg,
            DEFAULT_ACTIVITY,
            mapDiabetesType(user.getDiabetesType()),
            DEFAULT_MEAL_PATTERN);

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
    String curveJson;
    try {
      curveJson = objectMapper.writeValueAsString(aiResponse.curve());
    } catch (JsonProcessingException e) {
      throw new AiServiceException(AiServiceException.ErrorType.MODEL_ERROR, "곡선 데이터 직렬화 실패", e);
    }

    return predictionRepository.save(
        GlucosePrediction.builder()
            .user(user)
            .foodId(request.foodId())
            .foodName(request.foodName())
            .predictedCurve(curveJson)
            .predictedPeak(
                aiResponse.peakMgdl() != null ? BigDecimal.valueOf(aiResponse.peakMgdl()) : null)
            .build());
  }

  private PredictResponse toResponse(Integer predictionId, GlucosePredictResponse aiResponse) {
    return new PredictResponse(
        predictionId,
        aiResponse.curve(),
        aiResponse.peakMgdl(),
        aiResponse.peakMinute(),
        aiResponse.confidence());
  }
}
