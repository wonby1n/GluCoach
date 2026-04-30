package com.ssafy.s309.domain.prediction.client.dto;

public record UserProfile(
    String diabetesType, Boolean isMedicated, Float height, Float weight, Double currentGlucose) {}
