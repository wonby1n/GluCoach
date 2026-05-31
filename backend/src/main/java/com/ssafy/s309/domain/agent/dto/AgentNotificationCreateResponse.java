package com.ssafy.s309.domain.agent.dto;

public record AgentNotificationCreateResponse(Long chatMessageId, Boolean sent, Boolean skipped) {

  public static AgentNotificationCreateResponse ofCreated(Long chatMessageId) {
    return new AgentNotificationCreateResponse(chatMessageId, true, false);
  }

  public static AgentNotificationCreateResponse ofSkipped() {
    return new AgentNotificationCreateResponse(null, false, true);
  }
}
