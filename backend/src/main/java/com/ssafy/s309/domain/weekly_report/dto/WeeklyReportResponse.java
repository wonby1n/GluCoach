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

  private WeeklyReportResponse(WeeklyReport r, List<WeeklyFood> foods) {
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
  }

  public static WeeklyReportResponse from(WeeklyReport r, List<WeeklyFood> foods) {
    return new WeeklyReportResponse(r, foods);
  }

  @Getter
  public static class WeeklyFoodItem {

    private final Integer foodId;
    private final String foodName;
    private final String foodDisplayName;
    private final String type;
    private final BigDecimal avgSlope;

    private WeeklyFoodItem(WeeklyFood wf) {
      this.foodId = wf.getFoodId();
      this.foodName = wf.getFood() != null ? wf.getFood().getName() : null;
      this.foodDisplayName = wf.getFood() != null ? wf.getFood().getDisplayName() : null;
      this.type = wf.getType();
      this.avgSlope = wf.getAvgSlope();
    }

    public static WeeklyFoodItem from(WeeklyFood wf) {
      return new WeeklyFoodItem(wf);
    }
  }
}
