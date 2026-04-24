package com.ssafy.s309.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.auth.dto.*;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.auth.service.AuthService;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\.jwt\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private AuthService authService;

  private static final UUID USER_ID = UUID.randomUUID();

  private Authentication customAuth() {
    CustomUserPrincipal principal = new CustomUserPrincipal(USER_ID, "test@example.com");
    return new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
  }

  // ── 회원가입 ──────────────────────────────────────────────

  @Test
  void 회원가입_201_반환() throws Exception {
    SignupRequest request = new SignupRequest("test@example.com", "password123");
    TokenResponse response = new TokenResponse("access-token", "refresh-token");
    given(authService.signup(any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
  }

  @Test
  void 회원가입_이메일_중복_400_반환() throws Exception {
    SignupRequest request = new SignupRequest("dup@example.com", "password123");
    given(authService.signup(any())).willThrow(new IllegalArgumentException("이미 가입된 이메일입니다"));

    mockMvc
        .perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ── 로그인 ──────────────────────────────────────────────

  @Test
  void 로그인_200_반환() throws Exception {
    LoginRequest request = new LoginRequest("test@example.com", "password123");
    TokenResponse response = new TokenResponse("access-token", "refresh-token");
    given(authService.login(any())).willReturn(response);

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"));
  }

  @Test
  void 로그인_실패_400_반환() throws Exception {
    LoginRequest request = new LoginRequest("test@example.com", "wrong");
    given(authService.login(any()))
        .willThrow(new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  // ── 토큰 재발급 ──────────────────────────────────────────

  @Test
  void 토큰_재발급_200_반환() throws Exception {
    ReissueRequest request = new ReissueRequest("valid-refresh-token");
    TokenResponse response = new TokenResponse("new-access", "new-refresh");
    given(authService.reissue("valid-refresh-token")).willReturn(response);

    mockMvc
        .perform(
            post("/api/auth/reissue")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"));
  }

  // ── 로그아웃 ──────────────────────────────────────────────

  @Test
  void 로그아웃_204_반환() throws Exception {
    ReissueRequest request = new ReissueRequest("refresh-token");

    mockMvc
        .perform(
            post("/api/auth/logout")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());
  }

  // ── 회원탈퇴 ──────────────────────────────────────────────

  @Test
  void 회원탈퇴_204_반환() throws Exception {
    WithdrawRequest request = new WithdrawRequest("password123");
    doNothing().when(authService).withdraw(eq(USER_ID), eq("password123"));

    mockMvc
        .perform(
            delete("/api/auth/withdraw")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 회원탈퇴_비밀번호_불일치_400_반환() throws Exception {
    WithdrawRequest request = new WithdrawRequest("wrongPassword");
    doThrow(new IllegalArgumentException("비밀번호가 올바르지 않습니다"))
        .when(authService)
        .withdraw(eq(USER_ID), eq("wrongPassword"));

    mockMvc
        .perform(
            delete("/api/auth/withdraw")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 회원탈퇴_이미_탈퇴한_사용자_400_반환() throws Exception {
    WithdrawRequest request = new WithdrawRequest("password123");
    doThrow(new IllegalArgumentException("이미 탈퇴한 사용자입니다"))
        .when(authService)
        .withdraw(eq(USER_ID), eq("password123"));

    mockMvc
        .perform(
            delete("/api/auth/withdraw")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
