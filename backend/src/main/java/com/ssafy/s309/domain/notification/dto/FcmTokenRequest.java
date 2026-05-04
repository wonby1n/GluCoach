package com.ssafy.s309.domain.notification.dto;

import com.ssafy.s309.domain.notification.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;

public record FcmTokenRequest(@NotBlank String token, DeviceType deviceType) {}
