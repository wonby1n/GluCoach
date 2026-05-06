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
import com.ssafy.s309.domain.auth.service.EmailCheckRateLimiter;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

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
  @MockitoBean private EmailCheckRateLimiter emailCheckRateLimiter;

  private static final Integer USER_ID = 1;

  private Authentication customAuth() {
    CustomUserPrincipal principal = new CustomUserPrincipal(USER_ID, "test@example.com");
    return new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
  }

  @Test
  void 회원가입_201_반환() throws Exception {
    SignupRequest request =
        new SignupRequest("test@example.com", "password123", "테스트유저", "010-0000-0000");
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
    SignupRequest request =
        new SignupRequest("dup@example.com", "password123", "테스트유저", "010-0000-0000");
    given(authService.signup(any())).willThrow(new IllegalArgumentException("이미 가입된 이메일입니다"));

    mockMvc
        .perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

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

  @Test
  void 토큰_재발급_200_반환() throws Exception {
    ReissueRequest request = new ReissueRequest("valid-refresh-token");
    TokenResponse response = new TokenResponse("new-access", "new-refresh");
    given(authService.reissue("valid-refresh-token")).willReturn(response);

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-access"));
  }

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

  @Test
  void 비밀번호_변경_204_반환() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "newPassword123");
    doNothing()
        .when(authService)
        .changePassword(eq(USER_ID), eq("oldPassword"), eq("newPassword123"));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());
  }

  @Test
  void 비밀번호_변경_새_비밀번호_6자_미만_400_반환() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("oldPassword", "abc");

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("6자")));
  }

  @Test
  void 비밀번호_변경_현재_비밀번호_불일치_401_반환() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("wrongPassword", "newPassword123");
    doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다"))
        .when(authService)
        .changePassword(eq(USER_ID), eq("wrongPassword"), eq("newPassword123"));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("현재 비밀번호")));
  }

  @Test
  void 비밀번호_변경_새_비밀번호_동일_400_반환() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("samePassword", "samePassword");
    doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다"))
        .when(authService)
        .changePassword(eq(USER_ID), eq("samePassword"), eq("samePassword"));

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("새 비밀번호")));
  }

  @Test
  void 비밀번호_변경_현재_비밀번호_누락_400_반환() throws Exception {
    PasswordChangeRequest request = new PasswordChangeRequest("", "newPassword123");

    mockMvc
        .perform(
            put("/api/auth/password")
                .with(authentication(customAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 이메일_중복_확인_사용가능_200_반환() throws Exception {
    given(emailCheckRateLimiter.tryAcquireOrGetRetryAfter(any())).willReturn(null);
    given(authService.checkEmailAvailability("new@example.com")).willReturn(true);

    mockMvc
        .perform(get("/api/auth/email/check").param("email", "new@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
  }

  @Test
  void 이메일_중복_확인_이미_가입됨_200_반환() throws Exception {
    given(emailCheckRateLimiter.tryAcquireOrGetRetryAfter(any())).willReturn(null);
    given(authService.checkEmailAvailability("test@example.com")).willReturn(false);

    mockMvc
        .perform(get("/api/auth/email/check").param("email", "test@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.status").value("ALREADY_REGISTERED"))
        .andExpect(jsonPath("$.retryAfterSeconds").doesNotExist());
  }

  @Test
  void 이메일_중복_확인_형식_오류_400_반환() throws Exception {
    // 형식 검증이 rate limit 호출 전에 실패하므로 emailCheckRateLimiter 모킹 불필요
    mockMvc
        .perform(get("/api/auth/email/check").param("email", "not-an-email"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.status").value("INVALID_FORMAT"));
  }

  @Test
  void 이메일_중복_확인_파라미터_누락_400_반환() throws Exception {
    // required=false + 수동 검증으로 일관된 EmailCheckResponse(INVALID_FORMAT) 응답
    mockMvc
        .perform(get("/api/auth/email/check"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.status").value("INVALID_FORMAT"));
  }

  @Test
  void 이메일_중복_확인_rate_limit_초과_429_반환() throws Exception {
    given(emailCheckRateLimiter.tryAcquireOrGetRetryAfter(any())).willReturn(42);

    mockMvc
        .perform(get("/api/auth/email/check").param("email", "test@example.com"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.status").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(42));
  }
}
