package com.ssafy.s309.domain.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupRequest;
import com.ssafy.s309.domain.agent.dto.AgentScheduleFollowupResponse;
import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AgentScheduleFollowupServiceTest {

  @Mock private AgentPendingTriggerRepository repository;
  @InjectMocks private AgentScheduleFollowupService service;

  @Test
  void delay_30분_예약_scheduledAt_정확() {
    given(repository.save(any(AgentPendingTrigger.class)))
        .willAnswer(
            inv -> {
              AgentPendingTrigger t = inv.getArgument(0);
              return AgentPendingTrigger.builder()
                  .id(99)
                  .userId(t.getUserId())
                  .triggerType(t.getTriggerType())
                  .referenceId(t.getReferenceId())
                  .scheduledAt(t.getScheduledAt())
                  .build();
            });

    LocalDateTime before = LocalDateTime.now();
    AgentScheduleFollowupResponse res =
        service.schedule(new AgentScheduleFollowupRequest(3, "post_meal_followup", 12, 30));
    LocalDateTime after = LocalDateTime.now();

    assertThat(res.triggerId()).isEqualTo(99);
    assertThat(res.scheduledAt()).isAfterOrEqualTo(before.plusMinutes(30).minusSeconds(1));
    assertThat(res.scheduledAt()).isBeforeOrEqualTo(after.plusMinutes(30).plusSeconds(1));
  }

  @Test
  void save_호출되는_엔티티_필드_확인() {
    given(repository.save(any(AgentPendingTrigger.class)))
        .willAnswer(
            inv -> {
              AgentPendingTrigger t = inv.getArgument(0);
              return AgentPendingTrigger.builder()
                  .id(1)
                  .userId(t.getUserId())
                  .triggerType(t.getTriggerType())
                  .referenceId(t.getReferenceId())
                  .scheduledAt(t.getScheduledAt())
                  .build();
            });

    service.schedule(new AgentScheduleFollowupRequest(7, "post_meal_followup", 42, 60));

    ArgumentCaptor<AgentPendingTrigger> captor = ArgumentCaptor.forClass(AgentPendingTrigger.class);
    org.mockito.Mockito.verify(repository).save(captor.capture());
    AgentPendingTrigger saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(7);
    assertThat(saved.getTriggerType()).isEqualTo("post_meal_followup");
    assertThat(saved.getReferenceId()).isEqualTo(42);
  }

  @Test
  void referenceId_null_허용() {
    given(repository.save(any(AgentPendingTrigger.class)))
        .willAnswer(
            inv -> {
              AgentPendingTrigger t = inv.getArgument(0);
              return AgentPendingTrigger.builder()
                  .id(2)
                  .userId(t.getUserId())
                  .triggerType(t.getTriggerType())
                  .referenceId(t.getReferenceId())
                  .scheduledAt(t.getScheduledAt())
                  .build();
            });

    AgentScheduleFollowupResponse res =
        service.schedule(new AgentScheduleFollowupRequest(1, "post_meal_followup", null, 10));

    assertThat(res.triggerId()).isEqualTo(2);
  }
}
