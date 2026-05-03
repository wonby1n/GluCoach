package com.ssafy.s309.domain.agent.dto;

public record AgentNotificationCreateResponse(Integer alertId, Boolean sent, Boolean skipped) {

  public static AgentNotificationCreateResponse ofCreated(Integer alertId) {
    return new AgentNotificationCreateResponse(alertId, true, false);
  }

  public static AgentNotificationCreateResponse ofSkipped() {
    return new AgentNotificationCreateResponse(null, false, true);
  }
}
