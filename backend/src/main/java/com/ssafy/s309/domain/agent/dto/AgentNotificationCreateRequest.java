package com.ssafy.s309.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record AgentNotificationCreateRequest(
    @NotNull Integer userId,
    @NotBlank @Size(max = 50) String alertType,
    @NotBlank @Size(max = 500) String message,
    @NotNull @Size(min = 3, max = 3) List<Map<String, String>> options,
    Map<String, Object> displayTrace) {}
