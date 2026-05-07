package com.ssafy.s309.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.chat.dto.ChatReplyRequest;
import com.ssafy.s309.domain.chat.entity.ChatMessage;
import com.ssafy.s309.domain.chat.service.ChatMessageService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(
    controllers = ChatMessageController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class ChatMessageControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private ChatMessageService chatMessageService;

  private static final Integer USER_ID = 1;

  private final RequestPostProcessor authedUser =
      authentication(
          new UsernamePasswordAuthenticationToken(
              new CustomUserPrincipal(USER_ID, "test@example.com"), null, List.of()));

  /** Builder가 id를 안 받으니 reflection으로 주입 (테스트 픽스처 전용). */
  private ChatMessage withId(ChatMessage msg, Long id) {
    try {
      Field f = ChatMessage.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(msg, id);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return msg;
  }

  @Test
  void GET_messages_본인_메시지_페이징_unreadCount_포함() throws Exception {
    ChatMessage agentMsg =
        withId(
            ChatMessage.builder()
                .userId(USER_ID)
                .sender(ChatMessage.SENDER_AGENT)
                .alertType("AGENT_MEAL_FOLLOWUP")
                .message("산책할까요?")
                .options(
                    List.of(
                        Map.of("id", "walk_now", "label", "지금 산책"),
                        Map.of("id", "later", "label", "10분 후"),
                        Map.of("id", "skip", "label", "오늘은 패스")))
                .source(ChatMessage.SOURCE_AGENT)
                .build(),
            10L);
    given(chatMessageService.pageByUser(eq(USER_ID), anyInt(), anyInt()))
        .willReturn(new PageImpl<>(List.of(agentMsg)));
    given(chatMessageService.countUnread(USER_ID)).willReturn(3L);

    mockMvc
        .perform(get("/api/chat/messages").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(10))
        .andExpect(jsonPath("$.content[0].sender").value("agent"))
        .andExpect(jsonPath("$.content[0].options.length()").value(3))
        .andExpect(jsonPath("$.unreadCount").value(3));
  }

  @Test
  void POST_reply_정상_201_반환() throws Exception {
    ChatMessage userReply =
        withId(
            ChatMessage.builder()
                .userId(USER_ID)
                .sender(ChatMessage.SENDER_USER)
                .message("지금 산책")
                .parentId(10L)
                .selectedOptionId("walk_now")
                .build(),
            11L);
    given(chatMessageService.replyByOption(USER_ID, 10L, "walk_now")).willReturn(userReply);

    ChatReplyRequest req = new ChatReplyRequest(10L, "walk_now");
    mockMvc
        .perform(
            post("/api/chat/messages/reply")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(11))
        .andExpect(jsonPath("$.sender").value("user"))
        .andExpect(jsonPath("$.selectedOptionId").value("walk_now"))
        .andExpect(jsonPath("$.parentId").value(10));
  }

  @Test
  void POST_reply_optionId_없으면_400() throws Exception {
    given(chatMessageService.replyByOption(eq(USER_ID), eq(10L), eq("unknown")))
        .willThrow(
            new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "optionId not found in parent.options"));

    ChatReplyRequest req = new ChatReplyRequest(10L, "unknown");
    mockMvc
        .perform(
            post("/api/chat/messages/reply")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void POST_reply_타인_parent_403() throws Exception {
    given(chatMessageService.replyByOption(eq(USER_ID), eq(99L), any()))
        .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

    ChatReplyRequest req = new ChatReplyRequest(99L, "walk_now");
    mockMvc
        .perform(
            post("/api/chat/messages/reply")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isForbidden());
  }

  @Test
  void PATCH_read_204() throws Exception {
    mockMvc
        .perform(patch("/api/chat/messages/{id}/read", 10L).with(authedUser))
        .andExpect(status().isNoContent());
    verify(chatMessageService).markRead(USER_ID, 10L);
  }

  @Test
  void GET_unread_count() throws Exception {
    given(chatMessageService.countUnread(USER_ID)).willReturn(5L);

    mockMvc
        .perform(get("/api/chat/messages/unread-count").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unreadCount").value(5));
  }
}
