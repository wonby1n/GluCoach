package com.ssafy.s309.domain.food.dto;

public record FoodIngestResult(
    int totalRows, int inserted, int updated, int skippedInvalid, long elapsedMs) {}
