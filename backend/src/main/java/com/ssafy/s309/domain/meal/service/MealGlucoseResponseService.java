package com.ssafy.s309.domain.meal.service;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.meal.entity.MealGlucoseResponse;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import com.ssafy.s309.domain.meal.entity.UserFoodGrade;
import com.ssafy.s309.domain.meal.repository.MealGlucoseResponseRepository;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.meal.repository.UserFoodGradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealGlucoseResponseService {

  private static final int BASELINE_SEARCH_MINUTES = 60;
  private static final int RESPONSE_WINDOW_MINUTES = 120;

  private static final BigDecimal GRADE_S_MAX = new BigDecimal("0.5");
  private static final BigDecimal GRADE_A_MAX = new BigDecimal("1.0");
  private static final BigDecimal GRADE_B_MAX = new BigDecimal("1.4");
  private static final BigDecimal GRADE_C_MAX = new BigDecimal("1.7");

  private final MealRecordRepository mealRecordRepository;
  private final GlucoseRecordRepository glucoseRecordRepository;
  private final MealGlucoseResponseRepository mealGlucoseResponseRepository;
  private final UserFoodGradeRepository userFoodGradeRepository;

  @Transactional(readOnly = true)
  public List<Integer> findUnprocessedMealIds() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RESPONSE_WINDOW_MINUTES);
    return mealRecordRepository.findUnprocessedBefore(cutoff).stream()
        .map(MealRecord::getId)
        .toList();
  }

  // 각 meal을 독립된 트랜잭션으로 처리 — 실패해도 다른 meal에 영향 없음
  @Transactional
  public void processMeal(Integer mealId) {
    MealRecord meal = mealRecordRepository.findById(mealId).orElse(null);
    if (meal == null || Boolean.TRUE.equals(meal.getIsProcessed())) {
      return;
    }

    // response가 이미 저장됐는데 is_processed=false인 불일치 상태 복구
    if (mealGlucoseResponseRepository.existsByMealId(mealId)) {
      meal.markAsProcessed();
      log.warn("데이터 불일치 복구: response 존재하나 is_processed=false였음 mealId={}", mealId);
      return;
    }

    LocalDateTime mealTime = meal.getRecordedAt();

    // baseline: 식사 전 60분 이내 가장 최근 혈당 (오름차순 → 마지막 요소)
    List<GlucoseRecord> preRecords =
        glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            meal.getUserId(), mealTime.minusMinutes(BASELINE_SEARCH_MINUTES), mealTime);

    if (preRecords.isEmpty()) {
      log.info("baseline 혈당 없음, 스킵 mealId={}", meal.getId());
      return;
    }

    // peak: 식사 후 2시간 혈당 (식사 시각 1분 후부터 — baseline 측정값과 경계 중복 방지)
    List<GlucoseRecord> postRecords =
        glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            meal.getUserId(),
            mealTime.plusMinutes(1),
            mealTime.plusMinutes(RESPONSE_WINDOW_MINUTES));

    if (postRecords.isEmpty()) {
      log.info("식후 혈당 데이터 없음, 스킵 mealId={}", meal.getId());
      return;
    }

    GlucoseRecord baseline = preRecords.get(preRecords.size() - 1);
    GlucoseRecord peak =
        postRecords.stream().max(Comparator.comparing(GlucoseRecord::getValue)).orElseThrow();

    long minutes = ChronoUnit.MINUTES.between(mealTime, peak.getMeasuredAt());
    if (minutes == 0) {
      log.info("peak 측정 시각이 식사 시각과 동일, 스킵 mealId={}", meal.getId());
      return;
    }

    // slope (mg/dL/min): NUMERIC(3,1) → scale=1, HALF_UP
    BigDecimal slope =
        peak.getValue()
            .subtract(baseline.getValue())
            .divide(BigDecimal.valueOf(minutes), 1, RoundingMode.HALF_UP);

    mealGlucoseResponseRepository.save(
        MealGlucoseResponse.builder()
            .userId(meal.getUserId())
            .mealId(meal.getId())
            .baselineGlucoseId(baseline.getId())
            .peakGlucoseId(peak.getId())
            .slope(slope)
            .build());

    if (meal.getFoodId() != null) {
      upsertFoodGrade(meal.getUserId(), meal.getFoodId(), slope);
    }

    meal.markAsProcessed();

    log.info("식후 혈당 반응 저장 완료 mealId={}, slope={} mg/dL/min", meal.getId(), slope);
  }

  private void upsertFoodGrade(Integer userId, Integer foodId, BigDecimal newSlope) {
    Optional<UserFoodGrade> existing =
        userFoodGradeRepository.findByUserIdAndFoodId(userId, foodId);

    if (existing.isEmpty()) {
      userFoodGradeRepository.save(
          UserFoodGrade.builder()
              .userId(userId)
              .foodId(foodId)
              .avgSlope(newSlope)
              .grade(toGrade(newSlope))
              .mealCount(1)
              .build());
    } else {
      UserFoodGrade grade = existing.get();
      int newCount = grade.getMealCount() + 1;
      BigDecimal newAvg =
          grade
              .getAvgSlope()
              .multiply(BigDecimal.valueOf(grade.getMealCount()))
              .add(newSlope)
              .divide(BigDecimal.valueOf(newCount), 1, RoundingMode.HALF_UP);
      grade.update(newAvg, toGrade(newAvg), newCount);
    }
  }

  private String toGrade(BigDecimal slope) {
    if (slope.compareTo(GRADE_S_MAX) <= 0) return "S";
    if (slope.compareTo(GRADE_A_MAX) <= 0) return "A";
    if (slope.compareTo(GRADE_B_MAX) <= 0) return "B";
    if (slope.compareTo(GRADE_C_MAX) <= 0) return "C";
    return "D";
  }
}
