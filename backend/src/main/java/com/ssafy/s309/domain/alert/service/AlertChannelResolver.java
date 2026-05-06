package com.ssafy.s309.domain.alert.service;

/**
 * alert_type → FCM channel_id + 알림 제목 매핑 (plan D7 3채널). 룰 알림은 즉시 발송 / agent 알림은
 * AlertCreationService를 거쳐 dedup 후 발송.
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
    return switch (alertType) {
      case "HIGH" -> "⚠️ 고혈당 경고";
      case "LOW" -> "⚠️ 저혈당 경고";
      case "SOS" -> "🚨 SOS 알림";
      case "WEEKLY_REPORT" -> "주간 보고서 도착";
      case "AGENT_GLUCOSE_HIGH" -> "혈당이 올라가고 있어요";
      case "AGENT_GLUCOSE_LOW" -> "저혈당 주의";
      case "AGENT_MEAL_FOLLOWUP" -> "키키";
      case "AGENT_WAKE_UP" -> "키키";
      case "AGENT_SLEEP_INSIGHT" -> "수면 인사이트";
      default -> "GlucoCoach 알림";
    };
  }
}
