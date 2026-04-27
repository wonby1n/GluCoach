package com.ssafy.s309.common.service;

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
    for (String token : tokens) {
      try {
        Message message =
            Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build();
        FirebaseMessaging.getInstance().send(message);
      } catch (FirebaseMessagingException e) {
        log.warn("FCM 전송 실패 token={}: {}", token, e.getMessage());
      }
    }
  }
}
