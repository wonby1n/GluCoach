package com.ssafy.s309.domain.chat.controller;

import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.service.ChatFcmDispatcher;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "시연용 엔드포인트 — JWT 인증 불필요")
public class DemoController {

  private final ChatMessageService chatMessageService;
  private final ChatFcmDispatcher chatFcmDispatcher;

  private static final String POSTMEAL_MESSAGE =
      "하원님, 돼지국밥을 먹은 지 30분이 지났어요. 지금 혈당 추세로 보아 30분 후에는 200에 도달할 것으로 예상돼요. "
          + "하원님은 육류를 드셨을 때 고혈당이 오래 지속되시는 편이라, 산책으로 관리해보시는 건 어떨까요?";

  private static final String WALKING_FEEDBACK_MESSAGE =
      "하원님, 빠르게 걷고 계시네요! 그 패턴으로 계속 걸어보세요. 혈당이 훨씬 빨리 안정될 거예요 💪";

  @Operation(summary = "식후 알림 즉시 발송 (시연용)", description = "지정한 userId에게 하드코딩된 식후 혈당 알림을 즉시 발송한다.")
  @PostMapping("/postmeal-alert")
  public ResponseEntity<Void> postmealAlert(@RequestParam Integer userId) {
    ChatMessage msg =
        chatMessageService.insertAgent(
            userId, "AGENT_POST_MEAL", POSTMEAL_MESSAGE, null, null, null);
    chatFcmDispatcher.dispatch(userId, "AGENT_POST_MEAL", POSTMEAL_MESSAGE, msg.getId());
    return ResponseEntity.ok().build();
  }

  @Operation(
      summary = "걷기 피드백 알림 즉시 발송 (시연용)",
      description = "지정한 userId에게 하드코딩된 걷기 활동 피드백 알림을 즉시 발송한다.")
  @PostMapping("/walking-feedback")
  public ResponseEntity<Void> walkingFeedback(@RequestParam Integer userId) {
    ChatMessage msg =
        chatMessageService.insertAgent(
            userId, "AGENT_POST_MEAL", WALKING_FEEDBACK_MESSAGE, null, null, null);
    chatFcmDispatcher.dispatch(userId, "AGENT_POST_MEAL", WALKING_FEEDBACK_MESSAGE, msg.getId());
    return ResponseEntity.ok().build();
  }
}
