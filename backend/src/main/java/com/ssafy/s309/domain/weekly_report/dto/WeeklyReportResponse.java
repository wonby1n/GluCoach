package com.ssafy.s309.domain.weekly_report.dto;

import com.ssafy.s309.domain.weekly_report.entity.WeeklyFood;
import com.ssafy.s309.domain.weekly_report.entity.WeeklyReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class WeeklyReportResponse {

  private final Integer id;
  private final LocalDate weekStart;
  private final BigDecimal avgGlucose;
  private final BigDecimal minGlucose;
  private final BigDecimal maxGlucose;
  private final BigDecimal glucoseSd;
  private final BigDecimal timeInRange;
  private final BigDecimal timeAboveRange;
  private final BigDecimal timeBelowRange;
  private final String aiSummary;
  private final String aiSuggest;
  private final LocalDateTime createdAt;
  private final List<WeeklyFoodItem> foods;
  private final List<DailyGlucoseItem> dailyGlucose;

  private WeeklyReportResponse(
      WeeklyReport r, List<WeeklyFood> foods, List<DailyGlucoseItem> dailyGlucose) {
    this.id = r.getId();
    this.weekStart = r.getWeekStart();
    this.avgGlucose = r.getAvgGlucose();
    this.minGlucose = r.getMinGlucose();
    this.maxGlucose = r.getMaxGlucose();
    this.glucoseSd = r.getGlucoseSd();
    this.timeInRange = r.getTimeInRange();
    this.timeAboveRange = r.getTimeAboveRange();
    this.timeBelowRange = r.getTimeBelowRange();
    this.aiSummary = r.getAiSummary();
    this.aiSuggest = r.getAiSuggest();
    this.createdAt = r.getCreatedAt();
    this.foods = foods.stream().map(WeeklyFoodItem::from).toList();
    this.dailyGlucose = dailyGlucose;
  }

  public static WeeklyReportResponse from(
      WeeklyReport r, List<WeeklyFood> foods, List<DailyGlucoseItem> dailyGlucose) {
    return new WeeklyReportResponse(r, foods, dailyGlucose);
  }

  @Getter
  public static class DailyGlucoseItem {

    private final String date;
    private final BigDecimal avg;
    private final BigDecimal min;
    private final BigDecimal max;

    private DailyGlucoseItem(String date, BigDecimal avg, BigDecimal min, BigDecimal max) {
      this.date = date;
      this.avg = avg;
      this.min = min;
      this.max = max;
    }

    public static DailyGlucoseItem of(String date, BigDecimal avg, BigDecimal min, BigDecimal max) {
      return new DailyGlucoseItem(date, avg, min, max);
    }
  }

  @Getter
  public static class WeeklyFoodItem {

    private final Integer foodId;
    private final String foodName;
    private final String type;
    private final BigDecimal avgSlope;

    private WeeklyFoodItem(WeeklyFood wf) {
      this.foodId = wf.getFoodId();
      this.foodName = wf.getFood() != null ? wf.getFood().getName() : null;
      this.type = wf.getType();
      this.avgSlope = wf.getAvgSlope();
    }

    public static WeeklyFoodItem from(WeeklyFood wf) {
      return new WeeklyFoodItem(wf);
    }
  }
}
