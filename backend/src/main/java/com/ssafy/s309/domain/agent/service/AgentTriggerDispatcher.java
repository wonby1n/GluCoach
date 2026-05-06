package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentTriggerDispatchRequest;
import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTriggerDispatcher {

  private final RestClient aiRestClient;

  public void dispatch(AgentPendingTrigger trigger) {
    AgentTriggerDispatchRequest payload =
        new AgentTriggerDispatchRequest(
            trigger.getUserId(),
            trigger.getTriggerType(),
            trigger.getReferenceId(),
            trigger.getReason());

    aiRestClient.post().uri("/trigger").body(payload).retrieve().toBodilessEntity();

    log.info(
        "trigger dispatched: id={} type={} userId={}",
        trigger.getId(),
        trigger.getTriggerType(),
        trigger.getUserId());
  }
}
