package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.domain.alert.dto.AlertItemResponse;
import com.ssafy.s309.domain.alert.dto.AlertListResponse;
import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserAlertService {

  private final AlertRepository alertRepository;

  @Transactional(readOnly = true)
  public AlertListResponse list(Integer userId, Boolean isRead, int page, int size) {
    Page<Alert> result = alertRepository.findAlerts(userId, isRead, PageRequest.of(page, size));
    long unreadCount = alertRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
    return new AlertListResponse(
        result.getContent().stream().map(AlertItemResponse::from).toList(),
        unreadCount,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements());
  }

  @Transactional
  public void markRead(Integer userId, Integer alertId) {
    Alert alert =
        alertRepository
            .findByIdAndUserId(alertId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    alert.markRead();
  }
}
