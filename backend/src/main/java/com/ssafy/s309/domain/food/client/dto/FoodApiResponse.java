package com.ssafy.s309.domain.food.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FoodApiResponse(@JsonProperty("I2790") I2790 i2790) {

  public record I2790(
      @JsonProperty("total_count") String totalCount,
      @JsonProperty("row") List<FoodApiItem> rows,
      @JsonProperty("RESULT") Result result) {}

  public record Result(@JsonProperty("CODE") String code, @JsonProperty("MSG") String msg) {}
}
