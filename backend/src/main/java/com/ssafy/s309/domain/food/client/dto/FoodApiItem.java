package com.ssafy.s309.domain.food.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FoodApiItem(
    @JsonProperty("FOOD_CD") String foodCd,
    @JsonProperty("FOOD_NM_KR") String foodNm,
    @JsonProperty("FOOD_CAT1_NM") String categoryNm,
    @JsonProperty("SERVING_SIZE") String servingSize,
    @JsonProperty("AMT_NUM1") String kcal,
    @JsonProperty("AMT_NUM3") String proteinG,
    @JsonProperty("AMT_NUM4") String fatG,
    @JsonProperty("AMT_NUM6") String carbsG,
    @JsonProperty("AMT_NUM7") String sugarG,
    @JsonProperty("AMT_NUM8") String fiberG,
    @JsonProperty("AMT_NUM13") String sodiumMg,
    @JsonProperty("AMT_NUM23") String cholesterolMg,
    @JsonProperty("AMT_NUM24") String saturatedFatG,
    @JsonProperty("AMT_NUM25") String transFatG) {}
