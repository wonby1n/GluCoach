package com.ssafy.s309.domain.chat.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.chat.dto.ChatCommandRequest;
import com.ssafy.s309.domain.chat.dto.ChatMessageItem;
import com.ssafy.s309.domain.chat.dto.ChatMessageListResponse;
import com.ssafy.s309.domain.chat.dto.ChatReplyRequest;
import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
@Tag(name = "ChatMessages", description = "사용자 채팅 메시지 (agent/system 발신 + user 응답)")
public class ChatMessageController {

  private final ChatMessageService chatMessageService;

  @Operation(
      summary = "본인 채팅 메시지 페이징 조회",
      description =
          "최신순 (created_at DESC). agent/system/user 모든 sender 포함. unreadCount는 sender 무관 미읽음 개수.")
  @GetMapping
  public ResponseEntity<ChatMessageListResponse> list(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Parameter(description = "page index (0부터)", example = "0")
          @RequestParam(value = "page", defaultValue = "0")
          int page,
      @Parameter(description = "page size", example = "20")
          @RequestParam(value = "size", defaultValue = "20")
          int size) {
    Page<com.ssafy.s309.domain.chat.entity.ChatMessage> result =
        chatMessageService.pageByUser(principal.userId(), page, size);
    long unreadCount = chatMessageService.countUnread(principal.userId());
    return ResponseEntity.ok(
        new ChatMessageListResponse(
            result.getContent().stream().map(ChatMessageItem::from).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            unreadCount));
  }

  @Operation(
      summary = "사용자 응답 (옵션 선택)",
      description =
          "parent agent 메시지의 options 중 하나 선택. parent.options에서 label lookup 후 chat_messages INSERT(sender='user'). "
              + "parent 소유자 검증 / sender=agent 검증 / optionId 검증 실패 시 4xx.")
  @PostMapping("/reply")
  public ResponseEntity<ChatMessageItem> reply(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody ChatReplyRequest req) {
    ChatMessage saved =
        chatMessageService.replyByOption(principal.userId(), req.parentId(), req.optionId());
    return ResponseEntity.status(HttpStatus.CREATED).body(ChatMessageItem.from(saved));
  }

  @Operation(
      summary = "사용자 명령 발화",
      description =
          "사용자가 채팅 화면에서 미리 정의된 명령(예: 음식 추천)을 클릭. sender='user', command_type=NOT NULL, parent_id=NULL. "
              + "후속 agent 응답이 INSERT 시 이 메시지의 id를 parent_id로 참조.")
  @PostMapping("/command")
  public ResponseEntity<ChatMessageItem> command(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody ChatCommandRequest req) {
    ChatMessage saved =
        chatMessageService.insertUserCommand(
            principal.userId(), req.commandType(), req.message(), req.payload());
    return ResponseEntity.status(HttpStatus.CREATED).body(ChatMessageItem.from(saved));
  }

  @Operation(summary = "메시지 읽음 처리", description = "본인 메시지가 아니면 403.")
  @PatchMapping("/{id}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long id) {
    chatMessageService.markRead(principal.userId(), id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "안 읽음 카운트", description = "본인 메시지 중 is_read=false 개수.")
  @GetMapping("/unread-count")
  public ResponseEntity<UnreadCountResponse> unreadCount(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(
        new UnreadCountResponse(chatMessageService.countUnread(principal.userId())));
  }

  public record UnreadCountResponse(long unreadCount) {}
}
