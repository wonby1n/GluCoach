package com.ssafy.s309.domain.prediction.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FoodCompareAiRequest(
    @JsonProperty("user_id") String userId,
    @JsonProperty("user_name") String userName,
    @JsonProperty("food_a") FoodSummary foodA,
    @JsonProperty("food_b") FoodSummary foodB,
    @JsonProperty("user_profile") UserProfileSummary userProfile) {

  public record FoodSummary(
      String name,
      @JsonProperty("peak_mgdl") double peakMgdl,
      @JsonProperty("peak_minute") int peakMinute,
      double slope) {}

  public record UserProfileSummary(
      @JsonProperty("diabetes_type") String diabetesType,
      @JsonProperty("target_low") Double targetLow,
      @JsonProperty("target_high") Double targetHigh,
      @JsonProperty("age") Integer age,
      @JsonProperty("bmi") Double bmi,
      @JsonProperty("gender") String gender,
      @JsonProperty("is_medicated") Boolean isMedicated) {}
}
