package com.ssafy.s309.domain.prediction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.FoodNutrition;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.UserProfile;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.prediction.exception.AiServiceException;
import com.ssafy.s309.domain.prediction.repository.GlucosePredictionRepository;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PredictionService {

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
    FoodNutrition food =
        new FoodNutrition(
            request.foodId() != null ? String.valueOf(request.foodId()) : null,
            request.foodName(),
            request.carbsG(),
            request.proteinG(),
            request.fatG(),
            request.kcal(),
            request.sugarG(),
            request.giScore());

    UserProfile profile =
        new UserProfile(
            user.getDiabetesType() != null ? user.getDiabetesType().name() : null,
            user.getIsMedicated(),
            user.getHeight(),
            user.getWeight(),
            null); // currentGlucose: CGM 미구현

    return new GlucosePredictRequest(food, profile, "generic");
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
        aiResponse.returnMinute(),
        aiResponse.confidence());
  }
}
