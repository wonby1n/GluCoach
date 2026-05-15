package com.ssafy.s309.common.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
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
   * Android FcmService.kt 의 kiki_*_v3 채널과 일치해야 한다.
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
   */
  public void sendToTokens(
      List<String> tokens,
      String title,
      String body,
      String channelId,
      String alertType,
      Long chatMessageId) {
    AndroidConfig androidConfig =
        channelId != null
            ? AndroidConfig.builder()
                .setNotification(AndroidNotification.builder().setChannelId(channelId).build())
                .build()
            : null;
    for (String token : tokens) {
      try {
        Message.Builder builder =
            Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build());
        if (androidConfig != null) {
          builder.setAndroidConfig(androidConfig);
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
