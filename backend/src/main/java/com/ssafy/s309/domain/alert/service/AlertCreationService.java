package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 INSERT의 단일 진입점. plan D4 정책 ②: 같은 (user_id, alert_type, resolved_at IS NULL)이 최근 30분 내 존재하면
 * INSERT skip. 의미적 중복은 agent 자율 판단(정책 ④).
 *
 * <p>INSERT 성공 시 FCM 발사 (D7 channel_id 분기). 토큰 0개면 발사 skip — Alert는 그대로 보관.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertCreationService {

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

  private final AlertRepository alertRepository;
  private final NotificationTokenRepository tokenRepository;
  private final FcmService fcmService;

  @Transactional
  public CreationResult createIfNotDuplicate(
      Integer userId, String alertType, String message, String source) {
    LocalDateTime since = LocalDateTime.now().minus(DEDUP_WINDOW);
    boolean duplicate =
        alertRepository.existsByUserIdAndAlertTypeAndResolvedAtIsNullAndCreatedAtAfter(
            userId, alertType, since);
    if (duplicate) {
      log.debug(
          "Alert dedup skip: user={}, type={}, window={}m",
          userId,
          alertType,
          DEDUP_WINDOW.toMinutes());
      return CreationResult.skipped();
    }
    Alert saved =
        alertRepository.save(
            Alert.builder()
                .userId(userId)
                .alertType(alertType)
                .message(message)
                .source(source)
                .isRead(false)
                .build());

    dispatchFcm(userId, alertType, message);

    return CreationResult.created(saved.getId());
  }

  /** 사용자의 active FCM 토큰들에 channel_id 매핑한 발송. 토큰 0개면 silently skip. */
  private void dispatchFcm(Integer userId, String alertType, String message) {
    List<String> tokens =
        tokenRepository.findByUser_IdAndIsActiveTrue(userId).stream()
            .map(NotificationToken::getToken)
            .toList();
    if (tokens.isEmpty()) {
      log.debug("FCM dispatch skip: no active tokens for user={}", userId);
      return;
    }
    String title = AlertChannelResolver.resolveTitle(alertType);
    String channelId = AlertChannelResolver.resolveChannelId(alertType);
    fcmService.sendToTokens(tokens, title, message, channelId);
  }

  public record CreationResult(boolean created, Integer alertId) {
    public static CreationResult created(Integer id) {
      return new CreationResult(true, id);
    }

    public static CreationResult skipped() {
      return new CreationResult(false, null);
    }

    public Optional<Integer> alertIdOptional() {
      return Optional.ofNullable(alertId);
    }
  }
}
