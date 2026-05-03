package com.ssafy.s309.domain.alert.service;

import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 INSERT의 단일 진입점. plan D4 정책 ②: 같은 (user_id, alert_type, resolved_at IS NULL)이 최근 30분 내 존재하면
 * INSERT skip. 의미적 중복은 agent 자율 판단(정책 ④).
 *
 * <p>FCM 발사는 별도 task에서 wire-up 예정 (#1002).
 */
@Service
@RequiredArgsConstructor
public class AlertCreationService {

  private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

  private final AlertRepository alertRepository;

  @Transactional
  public CreationResult createIfNotDuplicate(
      Integer userId, String alertType, String message, String source) {
    LocalDateTime since = LocalDateTime.now().minus(DEDUP_WINDOW);
    boolean duplicate =
        alertRepository.existsByUserIdAndAlertTypeAndResolvedAtIsNullAndCreatedAtAfter(
            userId, alertType, since);
    if (duplicate) {
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
    return CreationResult.created(saved.getId());
  }

  public record CreationResult(boolean created, Long alertId) {
    public static CreationResult created(Long id) {
      return new CreationResult(true, id);
    }

    public static CreationResult skipped() {
      return new CreationResult(false, null);
    }

    public Optional<Long> alertIdOptional() {
      return Optional.ofNullable(alertId);
    }
  }
}
