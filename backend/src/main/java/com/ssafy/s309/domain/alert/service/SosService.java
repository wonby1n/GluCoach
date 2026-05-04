package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.alert.dto.SosRequest;
import com.ssafy.s309.domain.alert.dto.SosResponse;
import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.entity.GuardianNotification;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import com.ssafy.s309.domain.alert.repository.GuardianNotificationRepository;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.s309.domain.user.entity.WardGuardian;
import com.ssafy.s309.domain.user.repository.WardGuardianRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SosService {

  private final AlertRepository alertRepository;
  private final GuardianNotificationRepository guardianNotificationRepository;
  private final WardGuardianRepository wardGuardianRepository;
  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  @Transactional
  public SosResponse sos(Integer userId, SosRequest req) {
    Alert alert =
        alertRepository.save(
            Alert.builder()
                .userId(userId)
                .alertType("SOS")
                .source("be")
                .message("SOS 긴급 요청이 발생했습니다.")
                .latitude(req.latitude())
                .longitude(req.longitude())
                .isRead(false)
                .build());

    List<WardGuardian> guardians = wardGuardianRepository.findAllByWard_Id(userId);

    if (guardians.isEmpty()) {
      log.warn("SOS: no guardians registered for user={}", userId);
      return new SosResponse(alert.getId(), "SOS 요청이 접수되었습니다. 등록된 보호자가 없습니다.");
    }

    for (WardGuardian guardian : guardians) {
      guardianNotificationRepository.save(
          GuardianNotification.builder().alert(alert).wardGuardian(guardian).build());
      dispatchFcmToGuardian(guardian, userId);
    }

    return new SosResponse(alert.getId(), "SOS 요청이 접수되었습니다.");
  }

  private void dispatchFcmToGuardian(WardGuardian guardian, Integer wardUserId) {
    Integer guardianUserId = guardian.getGuardian().getId();
    List<String> tokens =
        tokenRepository.findByUser_IdAndIsActiveTrue(guardianUserId).stream()
            .map(NotificationToken::getToken)
            .toList();
    if (tokens.isEmpty()) {
      log.debug(
          "SOS FCM skip: guardian={} has no active tokens (ward={})", guardianUserId, wardUserId);
      return;
    }
    fcmService.sendToTokens(
        tokens,
        AlertChannelResolver.resolveTitle("SOS"),
        "피보호자의 SOS 긴급 요청이 발생했습니다.",
        AlertChannelResolver.CH_CRITICAL);
  }
}
