package com.ssafy.s309.domain.food.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FoodApiItem(
    @JsonProperty("FOOD_CD") String foodCd,
    @JsonProperty("DESC_KOR") String nameKor,
    @JsonProperty("NUTR_CONT1") String kcal,
    @JsonProperty("NUTR_CONT3") String proteinG,
    @JsonProperty("NUTR_CONT4") String fatG,
    @JsonProperty("NUTR_CONT6") String carbsG,
    @JsonProperty("NUTR_CONT7") String sugarG) {}
