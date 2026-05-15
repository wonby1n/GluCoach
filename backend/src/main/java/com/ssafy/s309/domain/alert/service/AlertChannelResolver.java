package com.ssafy.s309.domain.alert.service;

/**
 * alert_type → FCM channel_id + 알림 제목 매핑. chat_messages INSERT 직후 ChatFcmDispatcher가 호출.
 *
 * <p>채널 ID는 Android FcmService.kt 의 kiki_*_v3 채널과 일치해야 한다. 백그라운드 알림은 OS가 이 값으로 채널을 직접 조회하므로 불일치 시
 * 알림이 드롭된다.
 */
public final class AlertChannelResolver {

  private AlertChannelResolver() {}

  // Android FcmService.kt 의 CHANNEL_* 상수와 동일해야 함
  public static final String CH_DEFAULT = "kiki_default_v3";
  public static final String CH_WAKE_UP = "kiki_wake_up_v3";
  public static final String CH_MEAL_FOLLOWUP = "kiki_meal_followup_v3";
  public static final String CH_MEAL_REPLY = "kiki_meal_reply_v3";
  public static final String CH_MEAL_RETRY = "kiki_meal_retry_v3";
  public static final String CH_SLEEP_INSIGHT = "kiki_sleep_insight_v3";

  public static String resolveChannelId(String alertType) {
    if (alertType == null) return CH_DEFAULT;
    if (alertType.startsWith("AGENT_WAKE_UP")) return CH_WAKE_UP;
    if (alertType.startsWith("AGENT_MEAL_FOLLOWUP")) return CH_MEAL_FOLLOWUP;
    if (alertType.startsWith("AGENT_MEAL_REPLY")) return CH_MEAL_REPLY;
    if (alertType.startsWith("AGENT_MEAL_RETRY")) return CH_MEAL_RETRY;
    if (alertType.startsWith("AGENT_SLEEP_INSIGHT")) return CH_SLEEP_INSIGHT;
    return CH_DEFAULT;
  }

  public static String resolveTitle(String alertType) {
    return "키키";
  }
}
