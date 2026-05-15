package com.ssafy.s309.common.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {

  public void sendToTokens(List<String> tokens, String title, String body) {
    sendToTokens(tokens, title, body, null);
  }

  /**
   * Android NotificationChannel ID 분기. channelId는 AlertChannelResolver.resolveChannelId()로 결정하며
   * Android FcmService.kt 의 kiki_*_v4 채널과 일치해야 한다.
   */
  public void sendToTokens(List<String> tokens, String title, String body, String channelId) {
    sendToTokens(tokens, title, body, channelId, null);
  }

  /** alertType을 FCM data payload에 포함해 전송. FE가 message.data["alertType"]으로 버튼 분기를 결정한다. */
  public void sendToTokens(
      List<String> tokens, String title, String body, String channelId, String alertType) {
    sendToTokens(tokens, title, body, channelId, alertType, null);
  }

  /**
   * alertType + chatMessageId를 FCM data payload에 포함해 전송. FE가 푸시 클릭 시 chatMessageId로 단건 read 처리
   * (PATCH /api/chat/messages/{id}/read).
   *
   * <p>data-only payload 로 전송한다. notification payload를 포함하면 앱이 백그라운드일 때 Android가 알림을 직접 띄워 FE의
   * onMessageReceived() 가 호출되지 않고, 그 결과 TtsManager.speak() 도 실행되지 않아 본문 TTS 발화가 누락된다. 모든 알림은 FE가 직접
   * channelId 를 골라 NotificationCompat 으로 띄우고 동시에 TTS 를 발화한다.
   *
   * <p>priority HIGH 는 백그라운드/Doze 상태에서도 즉시 wake-up 되도록 보장한다.
   */
  public void sendToTokens(
      List<String> tokens,
      String title,
      String body,
      String channelId,
      String alertType,
      Long chatMessageId) {
    AndroidConfig androidConfig =
        AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build();
    for (String token : tokens) {
      try {
        Message.Builder builder =
            Message.builder()
                .setToken(token)
                .setAndroidConfig(androidConfig)
                .putData("title", title)
                .putData("body", body);
        if (channelId != null) {
          builder.putData("channelId", channelId);
        }
        if (alertType != null) {
          builder.putData("alertType", alertType);
        }
        if (chatMessageId != null) {
          builder.putData("chatMessageId", String.valueOf(chatMessageId));
        }
        FirebaseMessaging.getInstance().send(builder.build());
      } catch (FirebaseMessagingException e) {
        log.warn("FCM 전송 실패 token={}: {}", token, e.getMessage());
      }
    }
  }
}
