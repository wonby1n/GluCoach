package com.ssafy.s309.domain.sleep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import com.ssafy.s309.domain.sleep.dto.SleepSessionCreateRequest;
import com.ssafy.s309.domain.sleep.dto.SleepSessionResponse;
import com.ssafy.s309.domain.sleep.entity.SleepSession;
import com.ssafy.s309.domain.sleep.repository.SleepSessionRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SleepSessionServiceTest {

  @Mock private SleepSessionRepository sleepSessionRepository;
  @Mock private AgentPendingTriggerRepository triggerRepository;

  @InjectMocks private SleepSessionService service;

  @Test
  void 신규_세션_저장시_wake_up_트리거_INSERT() {
    Integer userId = 7;
    LocalDateTime start = LocalDateTime.of(2026, 5, 6, 23, 30);
    LocalDateTime end = LocalDateTime.of(2026, 5, 7, 7, 10);
    SleepSessionCreateRequest req = new SleepSessionCreateRequest(start, end, "samsung_health");

    when(sleepSessionRepository.findByUserIdAndStartedAt(userId, start))
        .thenReturn(Optional.empty());
    when(sleepSessionRepository.save(any(SleepSession.class)))
        .thenAnswer(inv -> withId(inv.getArgument(0), 99));

    SleepSessionResponse res = service.create(userId, req);

    assertThat(res.id()).isEqualTo(99);
    assertThat(res.startedAt()).isEqualTo(start);
    assertThat(res.endedAt()).isEqualTo(end);

    ArgumentCaptor<AgentPendingTrigger> captor = ArgumentCaptor.forClass(AgentPendingTrigger.class);
    verify(triggerRepository, times(1)).save(captor.capture());
    AgentPendingTrigger trigger = captor.getValue();
    assertThat(trigger.getTriggerType()).isEqualTo(AgentPendingTrigger.TYPE_WAKE_UP);
    assertThat(trigger.getReferenceId()).isEqualTo(99);
    assertThat(trigger.getUserId()).isEqualTo(userId);
    assertThat(trigger.getScheduledAt()).isEqualTo(end.plusMinutes(10));
  }

  @Test
  void 동일_started_at_재호출시_트리거_재발생_없음() {
    Integer userId = 7;
    LocalDateTime start = LocalDateTime.of(2026, 5, 6, 23, 30);
    LocalDateTime end = LocalDateTime.of(2026, 5, 7, 7, 10);
    SleepSessionCreateRequest req = new SleepSessionCreateRequest(start, end, "samsung_health");

    SleepSession existing =
        withId(
            SleepSession.builder()
                .userId(userId)
                .startedAt(start)
                .endedAt(end)
                .source("samsung_health")
                .build(),
            42);
    when(sleepSessionRepository.findByUserIdAndStartedAt(userId, start))
        .thenReturn(Optional.of(existing));

    SleepSessionResponse res = service.create(userId, req);

    assertThat(res.id()).isEqualTo(42);
    verify(sleepSessionRepository, never()).save(any(SleepSession.class));
    verify(triggerRepository, never()).save(any(AgentPendingTrigger.class));
  }

  @Test
  void endedAt이_startedAt_이전이면_거부() {
    Integer userId = 7;
    LocalDateTime start = LocalDateTime.of(2026, 5, 7, 7, 10);
    LocalDateTime end = LocalDateTime.of(2026, 5, 6, 23, 30);
    SleepSessionCreateRequest req = new SleepSessionCreateRequest(start, end, "samsung_health");

    assertThatThrownBy(() -> service.create(userId, req))
        .isInstanceOf(IllegalArgumentException.class);
    verify(sleepSessionRepository, never()).save(any(SleepSession.class));
    verify(triggerRepository, never()).save(any(AgentPendingTrigger.class));
  }

  private static SleepSession withId(SleepSession s, Integer id) {
    try {
      Field f = SleepSession.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(s, id);
      return s;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
