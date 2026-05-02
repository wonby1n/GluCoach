package com.ssafy.s309.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.user.dto.GuardianRequest;
import com.ssafy.s309.domain.user.dto.GuardianResponse;
import com.ssafy.s309.domain.user.dto.SettingsResponse;
import com.ssafy.s309.domain.user.dto.SettingsUpdateRequest;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UserController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private UserService userService;

  private static final Integer USER_ID = 1;
  private static final Integer WG_ID = 10;

  // ── Settings ──────────────────────────────────────────────

  @Test
  @WithMockUser
  void 설정_조회_200_반환() throws Exception {
    SettingsResponse response =
        new SettingsResponse(
            USER_ID,
            "홍길동",
            30,
            "male",
            "01012345678",
            170f,
            65f,
            DiabetesType.NORMAL,
            false,
            70,
            140,
            1);
    given(userService.getSettings(USER_ID)).willReturn(response);

    mockMvc
        .perform(get("/api/users/{userId}/settings", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID))
        .andExpect(jsonPath("$.targetLow").value(70))
        .andExpect(jsonPath("$.diabetesType").value("NORMAL"));
  }

  @Test
  @WithMockUser
  void 설정_수정_200_반환() throws Exception {
    SettingsUpdateRequest request =
        new SettingsUpdateRequest(
            "홍길동", 30, "male", "01012345678", 175f, 70f, DiabetesType.T1D, true, 80, 150, 1);
    SettingsResponse response =
        new SettingsResponse(
            USER_ID,
            "홍길동",
            30,
            "male",
            "01012345678",
            175f,
            70f,
            DiabetesType.T1D,
            true,
            80,
            150,
            1);
    given(userService.updateSettings(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/users/{userId}/settings", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.height").value(175f))
        .andExpect(jsonPath("$.isMedicated").value(true));
  }

  @Test
  @WithMockUser
  void 존재하지_않는_유저_설정_조회_400_반환() throws Exception {
    given(userService.getSettings(USER_ID))
        .willThrow(new IllegalArgumentException("존재하지 않는 유저입니다"));

    mockMvc
        .perform(get("/api/users/{userId}/settings", USER_ID))
        .andExpect(status().isBadRequest());
  }

  // ── Guardian ──────────────────────────────────────────────

  @Test
  @WithMockUser
  void 보호자_목록_조회_200_반환() throws Exception {
    List<GuardianResponse> list =
        List.of(
            new GuardianResponse(WG_ID, USER_ID, 2, "부모", 0),
            new GuardianResponse(11, USER_ID, 3, "배우자", 1));
    given(userService.getGuardians(USER_ID)).willReturn(list);

    mockMvc
        .perform(get("/api/users/{userId}/guardians", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].guardianId").value(2))
        .andExpect(jsonPath("$[0].priority").value(0));
  }

  @Test
  @WithMockUser
  void 보호자_등록_201_반환() throws Exception {
    GuardianRequest request = new GuardianRequest(2, "부모");
    GuardianResponse response = new GuardianResponse(WG_ID, USER_ID, 2, "부모", 0);
    given(userService.createGuardian(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/users/{userId}/guardians", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(WG_ID))
        .andExpect(jsonPath("$.priority").value(0));
  }

  @Test
  @WithMockUser
  void 보호자_등록_guardianId_누락_400_반환() throws Exception {
    String body = "{\"relation\":\"부모\"}";

    mockMvc
        .perform(
            post("/api/users/{userId}/guardians", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser
  void 보호자_수정_200_반환() throws Exception {
    GuardianRequest request = new GuardianRequest(2, "가족");
    GuardianResponse response = new GuardianResponse(WG_ID, USER_ID, 2, "가족", 0);
    given(userService.updateGuardian(eq(USER_ID), eq(WG_ID), any())).willReturn(response);

    mockMvc
        .perform(
            put("/api/users/{userId}/guardians/{wardGuardianId}", USER_ID, WG_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.relation").value("가족"));
  }

  @Test
  @WithMockUser
  void 보호자_삭제_204_반환() throws Exception {
    mockMvc
        .perform(
            delete("/api/users/{userId}/guardians/{wardGuardianId}", USER_ID, WG_ID).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser
  void 다른_유저의_보호자_삭제_400_반환() throws Exception {
    doThrow(new IllegalArgumentException("해당 유저의 보호자가 아닙니다"))
        .when(userService)
        .deleteGuardian(USER_ID, WG_ID);

    mockMvc
        .perform(
            delete("/api/users/{userId}/guardians/{wardGuardianId}", USER_ID, WG_ID).with(csrf()))
        .andExpect(status().isBadRequest());
  }
}
