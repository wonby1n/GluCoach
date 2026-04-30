package com.ssafy.s309.domain.prediction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AbPredictRequest(
    @NotNull @Valid PredictRequest foodA, @NotNull @Valid PredictRequest foodB) {}
