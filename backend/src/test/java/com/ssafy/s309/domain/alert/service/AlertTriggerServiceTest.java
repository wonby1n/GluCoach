package com.ssafy.s309.domain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.cgm.event.GlucoseReceivedEvent;
import com.ssafy.s309.domain.chat.service.ChatMessageCreationService;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AlertTriggerServiceTest {

  @Mock private ChatMessageCreationService chatMessageCreationService;
  @Mock private ChatMessageService chatMessageService;
  @InjectMocks private AlertTriggerService alertTriggerService;

  private static final Integer USER_ID = 1;
  private static final List<String> RULE_TYPES = List.of("HIGH", "LOW");

  private GlucoseReceivedEvent event(BigDecimal value) {
    return new GlucoseReceivedEvent(USER_ID, value, 100L);
  }

  @Test
  void HIGH_180_경계_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("180")));

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(chatMessageCreationService)
        .createIfNotDuplicate(eq(USER_ID), eq("HIGH"), messageCaptor.capture());
    assertThat(messageCaptor.getValue()).contains("180");
    verify(chatMessageService, never()).resolveOpenRule(any(), any());
  }

  @Test
  void HIGH_185_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("185")));

    verify(chatMessageCreationService).createIfNotDuplicate(eq(USER_ID), eq("HIGH"), any());
  }

  @Test
  void LOW_70_경계_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("70")));

    verify(chatMessageCreationService).createIfNotDuplicate(eq(USER_ID), eq("LOW"), any());
    verify(chatMessageService, never()).resolveOpenRule(any(), any());
  }

  @Test
  void LOW_65_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("65")));

    verify(chatMessageCreationService).createIfNotDuplicate(eq(USER_ID), eq("LOW"), any());
  }

  @Test
  void 정상_120_미해결_알림_있으면_모두_종결() {
    given(chatMessageService.resolveOpenRule(eq(USER_ID), eq(RULE_TYPES))).willReturn(2);

    alertTriggerService.handle(event(new BigDecimal("120")));

    verify(chatMessageCreationService, never()).createIfNotDuplicate(any(), any(), any());
    verify(chatMessageService).resolveOpenRule(eq(USER_ID), eq(RULE_TYPES));
  }

  @Test
  void 정상_120_미해결_알림_없으면_no_op() {
    given(chatMessageService.resolveOpenRule(eq(USER_ID), eq(RULE_TYPES))).willReturn(0);

    alertTriggerService.handle(event(new BigDecimal("120")));

    verify(chatMessageCreationService, never()).createIfNotDuplicate(any(), any(), any());
    verify(chatMessageService, times(1)).resolveOpenRule(eq(USER_ID), eq(RULE_TYPES));
  }

  @Test
  void 정상_71_경계_정상_복귀_분기() {
    given(chatMessageService.resolveOpenRule(eq(USER_ID), eq(RULE_TYPES))).willReturn(0);

    alertTriggerService.handle(event(new BigDecimal("71")));

    verify(chatMessageCreationService, never()).createIfNotDuplicate(any(), any(), any());
    verify(chatMessageService).resolveOpenRule(eq(USER_ID), eq(RULE_TYPES));
  }
}
