package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateRequest;
import com.ssafy.s309.domain.agent.dto.AgentNotificationCreateResponse;
import com.ssafy.s309.domain.agent.dto.AgentNotificationItem;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import com.ssafy.s309.domain.alert.service.AlertCreationService;
import com.ssafy.s309.domain.alert.service.AlertCreationService.CreationResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentNotificationService {

  private static final String AGENT_PREFIX = "AGENT_";
  private static final String SOURCE_AGENT = "agent";

  private final AlertCreationService alertCreationService;
  private final AlertRepository alertRepository;

  @Transactional(readOnly = true)
  public List<AgentNotificationItem> listRecent(Integer userId, Integer hours) {
    int safeHours = (hours == null || hours <= 0) ? 24 : Math.min(hours, 24 * 30);
    LocalDateTime since = LocalDateTime.now().minusHours(safeHours);
    return alertRepository
        .findByUserIdAndDeletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)
        .stream()
        .map(AgentNotificationItem::from)
        .toList();
  }

  @Transactional
  public CreationOutcome send(AgentNotificationCreateRequest req) {
    if (req.alertType() == null || !req.alertType().startsWith(AGENT_PREFIX)) {
      throw new IllegalArgumentException(
          "alert_type은 AGENT_ prefix로 시작해야 합니다. (룰 type은 BE 룰 트리거에서만 INSERT)");
    }
    CreationResult result =
        alertCreationService.createIfNotDuplicate(
            req.userId(), req.alertType(), req.message(), SOURCE_AGENT);
    return new CreationOutcome(
        result.created(),
        result.created()
            ? AgentNotificationCreateResponse.ofCreated(result.alertId())
            : AgentNotificationCreateResponse.ofSkipped());
  }

  /** Controller가 status code(201 vs 200) 분기에 사용. */
  public record CreationOutcome(boolean created, AgentNotificationCreateResponse body) {}
}
