package com.ssafy.s309.domain.food.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FoodApiItem(
    @JsonProperty("foodCd") String foodCd,
    @JsonProperty("foodNm") String foodNm,
    @JsonProperty("foodLv3Nm") String category,
    @JsonProperty("enerc") String kcal,
    @JsonProperty("chocdf") String carbsG,
    @JsonProperty("sugar") String sugarG,
    @JsonProperty("prot") String proteinG,
    @JsonProperty("fatce") String fatG,
    @JsonProperty("fibtg") String fiberG,
    @JsonProperty("fasat") String saturatedFatG,
    @JsonProperty("fatrn") String transFatG,
    @JsonProperty("chole") String cholesterolMg,
    @JsonProperty("nat") String sodiumMg) {}
