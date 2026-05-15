package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentFoodPredictionResult;
import com.ssafy.s309.domain.agent.dto.AgentFoodSearchItem;
import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import com.ssafy.s309.domain.prediction.service.PredictionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * STT 자유 발화 agent 가 음식 혈당 영향을 판단할 때 호출하는 서비스.
 *
 * <p>두 가지:
 *
 * <ul>
 *   <li>{@link #searchFoods(String, int)} — 사용자 발화에서 추출한 음식명으로 food_id 후보 검색.
 *   <li>{@link #predictForFoodByAgent(Integer, Integer)} — food_id 로 {@link
 *       PredictionService#predictForFood} 호출 후 LLM-친화 요약.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AgentFoodService {

  private static final double RISK_HIGH_MGDL = 200.0;
  private static final double RISK_ELEVATED_MGDL = 180.0;
  // 음식 검색 cachedAt 필터 — agent는 식약처 데이터 신선도보다 다양한 매칭이 중요.
  // 1년 이내로 넓게 잡아 STT 음식명 매칭률을 우선시.
  private static final int FOOD_CACHE_WINDOW_DAYS = 365;

  private final FoodRepository foodRepository;
  private final GlucoseRecordRepository glucoseRecordRepository;
  private final PredictionService predictionService;

  @Transactional(readOnly = true)
  public List<AgentFoodSearchItem> searchFoods(String query, int limit) {
    if (query == null || query.isBlank()) return List.of();
    int safeLimit = Math.max(1, Math.min(limit, 10));
    LocalDateTime since = LocalDateTime.now().minusDays(FOOD_CACHE_WINDOW_DAYS);

    // 정확 일치 우선, 없으면 부분 일치 fallback.
    List<Food> exact = foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc(query);
    if (exact.isEmpty()) {
      exact = foodRepository.findByDisplayNameIgnoreCaseOrderBySearchCountDesc(query);
    }
    List<Food> source =
        exact.isEmpty()
            ? foodRepository.findByNameContainingWithExactMatchFirst(
                query, since, org.springframework.data.domain.PageRequest.of(0, safeLimit))
            : exact;

    return source.stream()
        .limit(safeLimit)
        .map(
            f ->
                new AgentFoodSearchItem(
                    f.getId(),
                    f.getName(),
                    f.getDisplayName(),
                    f.getCategory(),
                    f.getKcal(),
                    f.getCarbsG()))
        .toList();
  }

  /**
   * food_id 로 혈당 예측 호출. PredictionService 가 GlucosePrediction row 를 저장하지만 agent 호출도 사용자 행동의 일부로 추적
   * 가능해야 하므로 OK.
   */
  public AgentFoodPredictionResult predictForFoodByAgent(Integer userId, Integer foodId) {
    Food food =
        foodRepository
            .findById(foodId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "food not found"));

    PredictResponse pred = predictionService.predictForFood(userId, food);

    Double currentMgdl =
        Optional.ofNullable(
                glucoseRecordRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId).orElse(null))
            .map(GlucoseRecord::getValue)
            .map(java.math.BigDecimal::doubleValue)
            .orElse(null);

    Double peak = pred.peakMgdl();
    Double delta = (peak != null && currentMgdl != null) ? peak - currentMgdl : null;

    String risk;
    if (peak == null) {
      risk = "unknown";
    } else if (peak >= RISK_HIGH_MGDL) {
      risk = "high";
    } else if (peak >= RISK_ELEVATED_MGDL) {
      risk = "elevated";
    } else {
      risk = "normal";
    }

    String displayName = food.getDisplayName() != null ? food.getDisplayName() : food.getName();
    return new AgentFoodPredictionResult(
        food.getId(), displayName, currentMgdl, peak, pred.peakMinute(), delta, risk);
  }
}
