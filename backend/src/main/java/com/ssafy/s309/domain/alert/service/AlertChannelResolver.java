package com.ssafy.s309.domain.alert.service;

/**
 * alert_type → FCM channel_id + 알림 제목 매핑 (plan D7 3채널). chat_messages INSERT 직후 ChatFcmDispatcher가
 * 호출.
 */
public final class AlertChannelResolver {

  private AlertChannelResolver() {}

  public static final String CH_CRITICAL = "glucose_critical";
  public static final String CH_COACHING = "glucose_coaching";
  public static final String CH_REPORT = "report_notification";

  public static String resolveChannelId(String alertType) {
    return switch (alertType) {
      case "HIGH", "LOW", "SOS", "AGENT_GLUCOSE_HIGH", "AGENT_GLUCOSE_LOW" -> CH_CRITICAL;
      case "WEEKLY_REPORT" -> CH_REPORT;
      default ->
          CH_COACHING; // AGENT_MEAL_FOLLOWUP / AGENT_WAKE_UP / AGENT_SLEEP_INSIGHT / AGENT_GENERIC
        // / 그 외
    };
  }

  public static String resolveTitle(String alertType) {
    return "키키";
  }
}
