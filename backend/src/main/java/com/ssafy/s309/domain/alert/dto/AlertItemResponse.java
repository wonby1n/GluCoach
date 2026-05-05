package com.ssafy.s309.domain.alert.dto;

import com.ssafy.s309.domain.alert.entity.Alert;
import java.time.LocalDateTime;

public record AlertItemResponse(
    Integer id, String alertType, String message, boolean isRead, LocalDateTime createdAt) {

  public static AlertItemResponse from(Alert alert) {
    return new AlertItemResponse(
        alert.getId(),
        alert.getAlertType(),
        alert.getMessage(),
        alert.getIsRead(),
        alert.getCreatedAt());
  }
}
