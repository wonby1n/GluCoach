package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.alert.dto.SosRequest;
import com.ssafy.s309.domain.alert.dto.SosResponse;
import com.ssafy.s309.domain.alert.entity.GuardianNotification;
import com.ssafy.s309.domain.alert.repository.GuardianNotificationRepository;
import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
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

  private static final String SOS_MESSAGE = "SOS 긴급 요청이 발생했습니다.";
  private static final String AUTO_SOS_MESSAGE = "혈당 위험 알림 미확인으로 보호자에게 자동 SOS가 발송되었습니다.";

  private final ChatMessageService chatMessageService;
  private final GuardianNotificationRepository guardianNotificationRepository;
  private final WardGuardianRepository wardGuardianRepository;
  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  @Transactional
  public SosResponse sos(Integer userId, SosRequest req) {
    ChatMessage chat = chatMessageService.insertSystem(userId, "SOS", SOS_MESSAGE);

    List<WardGuardian> guardians = wardGuardianRepository.findAllByWard_Id(userId);

    if (guardians.isEmpty()) {
      log.warn("SOS: no guardians registered for user={}", userId);
      return new SosResponse(chat.getId(), "SOS 요청이 접수되었습니다. 등록된 보호자가 없습니다.");
    }

    for (WardGuardian guardian : guardians) {
      guardianNotificationRepository.save(
          GuardianNotification.builder()
              .chatMessageId(chat.getId())
              .wardGuardian(guardian)
              .latitude(req.latitude())
              .longitude(req.longitude())
              .build());
      dispatchFcmToGuardian(guardian, userId);
    }

    return new SosResponse(chat.getId(), "SOS 요청이 접수되었습니다.");
  }

  /** 혈당 위험 알림 미확인 시 자동 SOS. 위치 정보 없음(null). */
  @Transactional
  public void autoSos(Integer userId) {
    ChatMessage chat = chatMessageService.insertSystem(userId, "SOS", AUTO_SOS_MESSAGE);
    List<WardGuardian> guardians = wardGuardianRepository.findAllByWard_Id(userId);
    if (guardians.isEmpty()) {
      log.warn("AutoSOS: no guardians for user={}", userId);
      return;
    }
    for (WardGuardian guardian : guardians) {
      guardianNotificationRepository.save(
          GuardianNotification.builder()
              .chatMessageId(chat.getId())
              .wardGuardian(guardian)
              .build());
      dispatchFcmToGuardian(guardian, userId);
    }
    log.info("AutoSOS dispatched: user={}, guardians={}", userId, guardians.size());
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
        AlertChannelResolver.CH_CRITICAL,
        "SOS");
  }
}
