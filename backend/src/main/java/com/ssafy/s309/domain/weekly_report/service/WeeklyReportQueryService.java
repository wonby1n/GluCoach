package com.ssafy.s309.domain.weekly_report.service;

import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.health.repository.DailyHealthSummaryRepository;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.medication.repository.MedicationRecordRepository;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest.DailyGlucoseItem;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest.FoodItem;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest.HourlyGlucoseItem;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyFoodItemProjection;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyGlucoseStatsProjection;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyHealthStatsProjection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyReportQueryService {

  private final GlucoseRecordRepository glucoseRecordRepository;
  private final MealRecordRepository mealRecordRepository;
  private final DailyHealthSummaryRepository dailyHealthSummaryRepository;
  private final MedicationRecordRepository medicationRecordRepository;

  /**
   * 주어진 사용자의 weekStart ~ weekStart+6일 데이터를 집계하여 AI 요청 DTO를 조립한다.
   *
   * @throws IllegalStateException 해당 기간 혈당 데이터가 없는 경우
   */
  public WeeklyReportAiRequest buildAiRequest(User user, LocalDate weekStart) {
    LocalDate weekEnd = weekStart.plusDays(6);
    LocalDateTime from = weekStart.atStartOfDay();
    LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();

    // ── 1. 혈당 통계 ─────────────────────────────────────────────
    WeeklyGlucoseStatsProjection stats =
        glucoseRecordRepository
            .findWeeklyGlucoseStats(
                user.getId(), from, to, user.getTargetLow(), user.getTargetHigh())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "혈당 데이터 없음: userId=" + user.getId() + " week=" + weekStart));

    if (stats.getAvgGlucose() == null) {
      throw new IllegalStateException("혈당 데이터 없음: userId=" + user.getId() + " week=" + weekStart);
    }

    // TAR + TBR → TIR 역산으로 합계 100% 보장
    BigDecimal tar = nullSafe(stats.getTimeAboveRange());
    BigDecimal tbr = nullSafe(stats.getTimeBelowRange());
    BigDecimal tir = BigDecimal.valueOf(100).subtract(tar).subtract(tbr);

    // ── 2. 시간대별 혈당 패턴 ────────────────────────────────────
    List<HourlyGlucoseItem> hourly =
        glucoseRecordRepository.findHourlyGlucosePattern(user.getId(), from, to).stream()
            .map(p -> HourlyGlucoseItem.builder().hour(p.getHour()).avg(p.getAvg()).build())
            .toList();

    // ── 3. 일별 혈당 추이 ─────────────────────────────────────────
    List<DailyGlucoseItem> daily =
        glucoseRecordRepository.findDailyGlucoseTrend(user.getId(), from, to).stream()
            .map(
                p ->
                    DailyGlucoseItem.builder()
                        .date(p.getDate().toString())
                        .avg(p.getAvg())
                        .min(p.getMin())
                        .max(p.getMax())
                        .build())
            .toList();

    // ── 4. 음식 데이터 ─────────────────────────────────────────────
    List<FoodItem> goodFoods =
        toFoodItems(mealRecordRepository.findWeeklyGoodFoods(user.getId(), from, to));
    List<FoodItem> badFoods =
        toFoodItems(mealRecordRepository.findWeeklyBadFoods(user.getId(), from, to));

    // ── 5. 식사 수 ────────────────────────────────────────────────
    long mealCount = mealRecordRepository.countByUserIdAndRecordedAtBetween(user.getId(), from, to);

    // ── 6. 활동/수면 ──────────────────────────────────────────────
    WeeklyHealthStatsProjection health =
        dailyHealthSummaryRepository
            .findWeeklyHealthStats(user.getId(), weekStart, weekEnd)
            .orElse(null);

    // ── 7. 복약 수 ────────────────────────────────────────────────
    long medicationCount =
        medicationRecordRepository.countByUserIdAndTakenAtBetween(user.getId(), from, to);

    log.info(
        "주간 보고서 데이터 집계 완료: userId={} week={} tir={} tar={} tbr={} mealCount={}",
        user.getId(),
        weekStart,
        tir,
        tar,
        tbr,
        mealCount);

    return WeeklyReportAiRequest.builder()
        .userId(user.getId())
        .weekStart(weekStart.toString())
        .weekEnd(weekEnd.toString())
        .userName(user.getName())
        .diabetesType(toDiabetesTypeString(user.getDiabetesType()))
        .targetLow(user.getTargetLow())
        .targetHigh(user.getTargetHigh())
        .avgGlucose(stats.getAvgGlucose())
        .minGlucose(stats.getMinGlucose())
        .maxGlucose(stats.getMaxGlucose())
        .glucoseSd(stats.getGlucoseSd())
        .timeInRange(tir.setScale(2, RoundingMode.HALF_UP))
        .timeAboveRange(tar)
        .timeBelowRange(tbr)
        .hourlyAvgGlucose(hourly)
        .dailyAvgGlucose(daily)
        .goodFoods(goodFoods)
        .badFoods(badFoods)
        .mealCount(mealCount)
        .weeklyAvgSteps(health != null ? health.getAvgSteps() : null)
        .weeklyTotalCalories(health != null ? health.getTotalCalories() : null)
        .weeklyAvgSleepMinutes(health != null ? health.getAvgSleepMinutes() : null)
        .medicationCount(medicationCount)
        .build();
  }

  private List<FoodItem> toFoodItems(List<WeeklyFoodItemProjection> projections) {
    return projections.stream()
        .map(p -> FoodItem.builder().foodName(p.getFoodName()).avgSlope(p.getAvgSlope()).build())
        .toList();
  }

  private String toDiabetesTypeString(DiabetesType type) {
    if (type == null) return "NORMAL";
    return switch (type) {
      case T1D -> "1";
      case T2D -> "2";
      case NORMAL -> "NORMAL";
    };
  }

  private BigDecimal nullSafe(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
