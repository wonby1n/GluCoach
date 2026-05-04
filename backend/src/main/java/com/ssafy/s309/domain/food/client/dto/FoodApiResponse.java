package com.ssafy.s309.domain.food.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FoodApiResponse(
    @JsonProperty("header") Header header, @JsonProperty("body") Body body) {

  public record Header(
      @JsonProperty("resultCode") String resultCode, @JsonProperty("resultMsg") String resultMsg) {}

  public record Body(
      @JsonProperty("pageNo") Integer pageNo,
      @JsonProperty("totalCount") Integer totalCount,
      @JsonProperty("numOfRows") Integer numOfRows,
      @JsonProperty("items") List<FoodApiItem> items) {}
}
