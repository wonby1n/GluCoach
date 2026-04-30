package com.ssafy.s309.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(@NotBlank String token, String deviceType) {}
