package com.ssafy.s309.domain.agent.service;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 사용자 command 발화 시 AI agent에 비동기 dispatch. AI는 처리 후 send_notification(parentChatMessageId 포함)으로 응답
 * INSERT를 트리거하므로 BE는 fire-and-forget.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentCommandClient {

  private final RestClient aiRestClient;

  // 명시적 executor 지정 — bare @Async 는 SimpleAsyncTaskExecutor 로 폴백해 무제한 스레드를 만든다.
  // asyncExecutor: core=4, max=16, queue=50 (AsyncConfig)
  @Async("asyncExecutor")
  public void dispatchAsync(
      Integer userId, Long chatMessageId, String commandType, Map<String, Object> payload) {
    Map<String, Object> body = new HashMap<>();
    body.put("user_id", userId);
    body.put("chat_message_id", chatMessageId);
    body.put("command_type", commandType);
    body.put("payload", payload != null ? payload : Map.of());
    try {
      aiRestClient.post().uri("/agent/command").body(body).retrieve().toBodilessEntity();
      log.info(
          "agent command dispatched: userId={} chatMessageId={} commandType={}",
          userId,
          chatMessageId,
          commandType);
    } catch (Exception e) {
      log.warn(
          "agent command dispatch failed: userId={} chatMessageId={} commandType={} err={}",
          userId,
          chatMessageId,
          commandType,
          e.getMessage());
    }
  }
}
