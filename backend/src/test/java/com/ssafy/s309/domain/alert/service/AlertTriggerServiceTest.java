package com.ssafy.s309.domain.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.alert.entity.Alert;
import com.ssafy.s309.domain.alert.repository.AlertRepository;
import com.ssafy.s309.domain.cgm.event.GlucoseReceivedEvent;
import java.math.BigDecimal;
import java.util.Collection;
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

  @Mock private AlertCreationService alertCreationService;
  @Mock private AlertRepository alertRepository;
  @InjectMocks private AlertTriggerService alertTriggerService;

  private static final Integer USER_ID = 1;

  private GlucoseReceivedEvent event(BigDecimal value) {
    return new GlucoseReceivedEvent(USER_ID, value, 100L);
  }

  @Test
  void HIGH_180_경계_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("180")));

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(alertCreationService)
        .createIfNotDuplicate(eq(USER_ID), eq("HIGH"), messageCaptor.capture(), eq("be"));
    assertThat(messageCaptor.getValue()).contains("180");
    verify(alertRepository, never()).findByUserIdAndAlertTypeInAndResolvedAtIsNull(any(), any());
  }

  @Test
  void HIGH_185_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("185")));

    verify(alertCreationService).createIfNotDuplicate(eq(USER_ID), eq("HIGH"), any(), eq("be"));
  }

  @Test
  void LOW_70_경계_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("70")));

    verify(alertCreationService).createIfNotDuplicate(eq(USER_ID), eq("LOW"), any(), eq("be"));
    verify(alertRepository, never()).findByUserIdAndAlertTypeInAndResolvedAtIsNull(any(), any());
  }

  @Test
  void LOW_65_알림_생성() {
    alertTriggerService.handle(event(new BigDecimal("65")));

    verify(alertCreationService).createIfNotDuplicate(eq(USER_ID), eq("LOW"), any(), eq("be"));
  }

  @Test
  void 정상_120_미해결_알림_있으면_모두_종결() {
    Alert openHigh =
        Alert.builder()
            .userId(USER_ID)
            .alertType("HIGH")
            .message("..")
            .source("be")
            .isRead(false)
            .build();
    Alert openLow =
        Alert.builder()
            .userId(USER_ID)
            .alertType("LOW")
            .message("..")
            .source("be")
            .isRead(false)
            .build();
    given(
            alertRepository.findByUserIdAndAlertTypeInAndResolvedAtIsNull(
                eq(USER_ID), any(Collection.class)))
        .willReturn(List.of(openHigh, openLow));

    alertTriggerService.handle(event(new BigDecimal("120")));

    verify(alertCreationService, never()).createIfNotDuplicate(any(), any(), any(), any());
    assertThat(openHigh.getResolvedAt()).isNotNull();
    assertThat(openLow.getResolvedAt()).isNotNull();
  }

  @Test
  void 정상_120_미해결_알림_없으면_no_op() {
    given(
            alertRepository.findByUserIdAndAlertTypeInAndResolvedAtIsNull(
                eq(USER_ID), any(Collection.class)))
        .willReturn(List.of());

    alertTriggerService.handle(event(new BigDecimal("120")));

    verify(alertCreationService, never()).createIfNotDuplicate(any(), any(), any(), any());
    verify(alertRepository, times(1))
        .findByUserIdAndAlertTypeInAndResolvedAtIsNull(eq(USER_ID), any(Collection.class));
  }

  @Test
  void 정상_71_경계_정상_복귀_분기() {
    given(
            alertRepository.findByUserIdAndAlertTypeInAndResolvedAtIsNull(
                eq(USER_ID), any(Collection.class)))
        .willReturn(List.of());

    alertTriggerService.handle(event(new BigDecimal("71")));

    verify(alertCreationService, never()).createIfNotDuplicate(any(), any(), any(), any());
    verify(alertRepository)
        .findByUserIdAndAlertTypeInAndResolvedAtIsNull(eq(USER_ID), any(Collection.class));
  }
}
