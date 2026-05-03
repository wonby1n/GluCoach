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
   * Android NotificationChannel ID 분기 (plan D7): glucose_critical / glucose_coaching /
   * report_notification. channelId가 null이면 기본 채널로 전송.
   */
  public void sendToTokens(List<String> tokens, String title, String body, String channelId) {
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
        FirebaseMessaging.getInstance().send(builder.build());
      } catch (FirebaseMessagingException e) {
        log.warn("FCM 전송 실패 token={}: {}", token, e.getMessage());
      }
    }
  }
}
