package com.ssafy.s309.domain.prediction.service;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import com.ssafy.s309.domain.prediction.client.FoodCompareExplainClient;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiRequest;
import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiRequest.FoodSummary;
import com.ssafy.s309.domain.prediction.client.dto.FoodCompareAiRequest.UserProfileSummary;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.client.dto.MealInfo;
import com.ssafy.s309.domain.prediction.client.dto.UserProfileWithPattern;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.CompareExplainRequest;
import com.ssafy.s309.domain.prediction.dto.CompareExplainResponse;
import com.ssafy.s309.domain.prediction.dto.CurvePoint;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 식전 혈당 예측. AI 모델 호출 + 결과 저장.
 *
 * <p>트랜잭션 분리: AI 외부 호출(GlucosePredictClient)을 트랜잭션 밖에서 수행하기 위해 본 클래스 자체는 {@code @Transactional} 을
 * 갖지 않는다. DB 조회(findUser)와 쓰기(savePrediction)는 {@link PredictionTxHelper} 의 짧은 tx 메서드들로 분리.
 */
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
  private final FoodCompareExplainClient foodCompareExplainClient;
  private final PredictionTxHelper tx;
  private final FoodRepository foodRepository;
  private final Executor asyncExecutor;

  public PredictResponse predict(Integer userId, PredictRequest request) {
    User user = tx.findUser(userId);
    return predictWithUser(user, request);
  }

  /**
   * Food 엔티티 직접 입력 변종 — 사진 통합 예측({@code /from-image}) 흐름에서 사용.
   *
   * <p>호출자(orchestrator)는 {@code food.carbsG NOT NULL} 보장 시점에만 호출해야 한다. Food 엔티티를 직접 받으므로 DB 재조회 없이
   * macro 필드를 AI 요청에 전달한다.
   */
  public PredictResponse predictForFood(Integer userId, Food food) {
    User user = tx.findUser(userId);
    PredictRequest request =
        new PredictRequest(
            food.getId(),
            food.getName(),
            food.getCarbsG(),
            food.getProteinG(),
            food.getFatG(),
            food.getFiberG(),
            food.getKcal(),
            food.getSugarG(),
            null);
    GlucosePredictResponse aiResponse =
        glucosePredictClient.predict(buildAiRequest(user, request, Optional.of(food)));
    GlucosePrediction saved = tx.savePrediction(userId, request, aiResponse);
    return toResponse(saved.getId(), aiResponse);
  }

  public CompareExplainResponse compareExplain(Integer userId, CompareExplainRequest request) {
    User user = tx.findUser(userId);
    String diabetesType = mapDiabetesType(user.getDiabetesType());
    Double targetLow = user.getTargetLow() != null ? user.getTargetLow().doubleValue() : null;
    Double targetHigh = user.getTargetHigh() != null ? user.getTargetHigh().doubleValue() : null;

    FoodCompareAiRequest aiRequest =
        new FoodCompareAiRequest(
            String.valueOf(userId),
            user.getName(),
            new FoodSummary(
                request.foodAName(),
                request.foodAPeakMgdl(),
                request.foodAPeakMinute(),
                request.foodASlope()),
            new FoodSummary(
                request.foodBName(),
                request.foodBPeakMgdl(),
                request.foodBPeakMinute(),
                request.foodBSlope()),
            new UserProfileSummary(diabetesType, targetLow, targetHigh));

    return new CompareExplainResponse(foodCompareExplainClient.explain(aiRequest).message());
  }

  public AbPredictResponse comparePredict(Integer userId, AbPredictRequest request) {
    User user = tx.findUser(userId);

    // asyncExecutor 사용 — MdcTaskDecorator 가 호출 스레드 MDC(correlationId 포함) 를 워커로 전파.
    // commonPool 로 두면 두 worker 가 각자 새 correlationId 를 발급해 추적성이 끊김.
    CompletableFuture<PredictResponse> futureA =
        CompletableFuture.supplyAsync(() -> predictWithUser(user, request.foodA()), asyncExecutor);
    CompletableFuture<PredictResponse> futureB =
        CompletableFuture.supplyAsync(() -> predictWithUser(user, request.foodB()), asyncExecutor);

    return new AbPredictResponse(futureA.join(), futureB.join());
  }

  /**
   * AI 호출 (tx 밖) → DB 저장 (짧은 tx) → 응답 변환. predict / comparePredict 의 핵심 흐름 공통.
   *
   * <p>buildAiRequest 는 user 객체의 단순 칼럼(weight/diabetesType)만 접근하므로 detached 상태로 OK. savePrediction
   * 은 userId 만 넘겨 새 tx 에서 proxy 를 발급하게 함 — detached entity 가 새 tx 의 association 으로 흘러들어가는 fragility
   * 회피.
   */
  private PredictResponse predictWithUser(User user, PredictRequest request) {
    Optional<Food> foodOpt =
        request.foodId() != null ? foodRepository.findById(request.foodId()) : Optional.empty();
    GlucosePredictResponse aiResponse =
        glucosePredictClient.predict(buildAiRequest(user, request, foodOpt));
    GlucosePrediction saved = tx.savePrediction(user.getId(), request, aiResponse);
    return toResponse(saved.getId(), aiResponse);
  }

  private GlucosePredictRequest buildAiRequest(
      User user, PredictRequest request, Optional<Food> foodOpt) {
    // foods 테이블 영양소는 100g 기준 저장 → 1인분(servingSize g) 기준으로 환산.
    double servingScale =
        foodOpt
            .map(Food::getServingSize)
            .filter(s -> s != null && s.compareTo(BigDecimal.ZERO) > 0)
            .map(s -> s.doubleValue() / 100.0)
            .orElse(1.0);

    // foodId가 있으면 DB 값 우선. foods.carbs_g 가 NULL/0이면 클라이언트 값으로 fallback.
    // AI 모델이 macro 4개(carbs/protein/fat/fiber)로 곡선 계산하므로 NULL은 0.0 으로 치환해야
    // 예측 그래프가 정상 반환된다 (NULL 시 AI 단에서 계산 실패).
    double carbs =
        foodOpt
            .map(Food::getCarbsG)
            .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
            .map(v -> v.doubleValue() * servingScale)
            .orElseGet(() -> request.carbsG() != null ? request.carbsG().doubleValue() : 0.0);

    // macro: foodId 있으면 DB 값(1인분 환산), 없으면 클라이언트 요청값, 둘 다 없으면 0.0 fallback.
    Double proteinG =
        foodOpt
            .map(Food::getProteinG)
            .map(v -> v.doubleValue() * servingScale)
            .orElseGet(() -> request.proteinG() != null ? request.proteinG().doubleValue() : 0.0);
    Double fatG =
        foodOpt
            .map(Food::getFatG)
            .map(v -> v.doubleValue() * servingScale)
            .orElseGet(() -> request.fatG() != null ? request.fatG().doubleValue() : 0.0);
    Double fiberG =
        foodOpt
            .map(Food::getFiberG)
            .map(v -> v.doubleValue() * servingScale)
            .orElseGet(() -> request.fiberG() != null ? request.fiberG().doubleValue() : 0.0);
    Double kcal =
        foodOpt
            .map(Food::getKcal)
            .map(v -> v.doubleValue() * servingScale)
            .orElseGet(() -> request.kcal() != null ? request.kcal().doubleValue() : 0.0);

    MealInfo meal =
        new MealInfo(carbs, LocalDateTime.now(KST).toString(), proteinG, fatG, fiberG, kcal);

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
