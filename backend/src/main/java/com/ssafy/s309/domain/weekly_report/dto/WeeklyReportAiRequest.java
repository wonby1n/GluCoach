package com.ssafy.s309.domain.weekly_report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeeklyReportAiRequest {

  @JsonProperty("user_id")
  private Integer userId;

  @JsonProperty("week_start")
  private String weekStart;

  @JsonProperty("week_end")
  private String weekEnd;

  @JsonProperty("user_name")
  private String userName;

  @JsonProperty("diabetes_type")
  private String diabetesType;

  @JsonProperty("target_low")
  private BigDecimal targetLow;

  @JsonProperty("target_high")
  private BigDecimal targetHigh;

  @JsonProperty("avg_glucose")
  private BigDecimal avgGlucose;

  @JsonProperty("min_glucose")
  private BigDecimal minGlucose;

  @JsonProperty("max_glucose")
  private BigDecimal maxGlucose;

  @JsonProperty("glucose_sd")
  private BigDecimal glucoseSd;

  @JsonProperty("time_in_range")
  private BigDecimal timeInRange;

  @JsonProperty("time_above_range")
  private BigDecimal timeAboveRange;

  @JsonProperty("time_below_range")
  private BigDecimal timeBelowRange;

  @JsonProperty("hourly_avg_glucose")
  private List<HourlyGlucoseItem> hourlyAvgGlucose;

  @JsonProperty("daily_avg_glucose")
  private List<DailyGlucoseItem> dailyAvgGlucose;

  @JsonProperty("good_foods")
  private List<FoodItem> goodFoods;

  @JsonProperty("bad_foods")
  private List<FoodItem> badFoods;

  @JsonProperty("meal_count")
  private long mealCount;

  @JsonProperty("weekly_avg_steps")
  private BigDecimal weeklyAvgSteps;

  @JsonProperty("weekly_total_calories")
  private BigDecimal weeklyTotalCalories;

  @JsonProperty("weekly_avg_sleep_minutes")
  private BigDecimal weeklyAvgSleepMinutes;

  @JsonProperty("medication_count")
  private long medicationCount;

  @Getter
  @Builder
  public static class HourlyGlucoseItem {
    @JsonProperty("hour")
    private Integer hour;

    @JsonProperty("avg")
    private BigDecimal avg;
  }

  @Getter
  @Builder
  public static class DailyGlucoseItem {
    @JsonProperty("date")
    private String date;

    @JsonProperty("avg")
    private BigDecimal avg;

    @JsonProperty("min")
    private BigDecimal min;

    @JsonProperty("max")
    private BigDecimal max;
  }

  @Getter
  @Builder
  public static class FoodItem {
    @JsonProperty("food_name")
    private String foodName;

    @JsonProperty("avg_slope")
    private BigDecimal avgSlope;
  }
}
