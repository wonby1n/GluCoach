package com.ssafy.s309.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.auth.dto.LoginRequest;
import com.ssafy.s309.domain.auth.dto.SignupRequest;
import com.ssafy.s309.domain.auth.dto.TokenResponse;
import com.ssafy.s309.domain.auth.jwt.JwtProvider;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private AuthService authService;

  private static final Integer USER_ID = 1;
  private User user;

  @BeforeEach
  void setUp() {
    user = User.builder().email("test@example.com").password("encodedPassword").build();
    ReflectionTestUtils.setField(user, "id", USER_ID);
  }

  @Test
  void 회원가입_성공() {
    SignupRequest request =
        new SignupRequest("new@example.com", "password123", "테스트유저", "010-0000-0000");
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(passwordEncoder.encode("password123")).willReturn("encodedPassword");
    given(userRepository.save(any(User.class)))
        .willAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", USER_ID);
              return saved;
            });
    given(jwtProvider.generateAccessToken(any(), anyString())).willReturn("access-token");
    given(jwtProvider.generateRefreshToken(any())).willReturn("refresh-token");

    TokenResponse response = authService.signup(request);

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    verify(refreshTokenService).save(any(), anyString());
  }

  @Test
  void 회원가입_이메일_중복_예외() {
    SignupRequest request =
        new SignupRequest("test@example.com", "password123", "테스트유저", "010-0000-0000");
    given(userRepository.existsByEmail("test@example.com")).willReturn(true);

    assertThatThrownBy(() -> authService.signup(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 가입된 이메일");
  }

  @Test
  void 로그인_성공() {
    LoginRequest request = new LoginRequest("test@example.com", "password123");
    given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
        .willReturn(Optional.of(user));
    given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);
    given(jwtProvider.generateAccessToken(USER_ID, "test@example.com")).willReturn("access-token");
    given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

    TokenResponse response = authService.login(request);

    assertThat(response.accessToken()).isEqualTo("access-token");
  }

  @Test
  void 로그인_존재하지_않는_이메일_예외() {
    LoginRequest request = new LoginRequest("nobody@example.com", "password123");
    given(userRepository.findByEmailAndDeletedAtIsNull("nobody@example.com"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
  }

  @Test
  void 로그인_비밀번호_불일치_예외() {
    LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
    given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
        .willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
  }

  @Test
  void 로그아웃_성공_리프레시_토큰_삭제() {
    given(jwtProvider.validate("valid-refresh")).willReturn(true);
    given(jwtProvider.getUserId("valid-refresh")).willReturn(USER_ID);

    authService.logout("valid-refresh");

    verify(refreshTokenService).delete(USER_ID);
  }

  @Test
  void 로그아웃_유효하지_않은_토큰_무시() {
    given(jwtProvider.validate("invalid-token")).willReturn(false);

    authService.logout("invalid-token");

    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 회원탈퇴_성공_소프트삭제_및_익명화() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password123", "encodedPassword")).willReturn(true);

    authService.withdraw(USER_ID, "password123");

    assertThat(user.isDeleted()).isTrue();
    assertThat(user.getDeletedAt()).isNotNull();
    assertThat(user.getEmail()).startsWith("deleted_");
    assertThat(user.getEmail()).endsWith("@withdrawn.local");
    assertThat(user.getPassword()).isNull();
    assertThat(user.getHeight()).isNull();
    assertThat(user.getWeight()).isNull();
    verify(refreshTokenService).delete(USER_ID);
  }

  @Test
  void 회원탈퇴_비밀번호_불일치_예외() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

    assertThatThrownBy(() -> authService.withdraw(USER_ID, "wrongPassword"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("비밀번호가 올바르지 않습니다");

    assertThat(user.isDeleted()).isFalse();
    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 회원탈퇴_이미_탈퇴한_사용자_예외() {
    user.withdraw();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.withdraw(USER_ID, "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 탈퇴한 사용자");
  }

  @Test
  void 회원탈퇴_존재하지_않는_사용자_예외() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.withdraw(USER_ID, "password123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 사용자");
  }

  @Test
  void 회원탈퇴_OAuth_사용자_비밀번호_없이_성공() {
    User oauthUser = User.builder().email("oauth@example.com").provider("kakao").build();
    ReflectionTestUtils.setField(oauthUser, "id", USER_ID);

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(oauthUser));

    authService.withdraw(USER_ID, null);

    assertThat(oauthUser.isDeleted()).isTrue();
    assertThat(oauthUser.getEmail()).startsWith("deleted_");
    verify(refreshTokenService).delete(USER_ID);
  }

  @Test
  void 탈퇴한_사용자_로그인_불가() {
    user.withdraw();
    given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
        .willReturn(Optional.empty());

    LoginRequest request = new LoginRequest("test@example.com", "password123");

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이메일 또는 비밀번호가 올바르지 않습니다");
  }

  @Test
  void 비밀번호_변경_성공_새_해시_저장_및_RefreshToken_무효화() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("oldPassword", "encodedPassword")).willReturn(true);
    given(passwordEncoder.encode("newPassword123")).willReturn("newEncodedPassword");

    authService.changePassword(USER_ID, "oldPassword", "newPassword123");

    assertThat(user.getPassword()).isEqualTo("newEncodedPassword");
    verify(refreshTokenService).delete(USER_ID);
  }

  @Test
  void 비밀번호_변경_현재_비밀번호_불일치_401() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

    ResponseStatusException ex =
        catchThrowableOfType(
            ResponseStatusException.class,
            () -> authService.changePassword(USER_ID, "wrongPassword", "newPassword123"));

    assertThat(ex).isNotNull();
    assertThat(ex.getStatusCode().value()).isEqualTo(401);
    assertThat(ex.getReason()).contains("현재 비밀번호");
    assertThat(user.getPassword()).isEqualTo("encodedPassword");
    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 비밀번호_변경_새_비밀번호가_현재와_동일_400() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("samePassword", "encodedPassword")).willReturn(true);

    ResponseStatusException ex =
        catchThrowableOfType(
            ResponseStatusException.class,
            () -> authService.changePassword(USER_ID, "samePassword", "samePassword"));

    assertThat(ex).isNotNull();
    assertThat(ex.getStatusCode().value()).isEqualTo(400);
    assertThat(ex.getReason()).contains("새 비밀번호");
    assertThat(user.getPassword()).isEqualTo("encodedPassword");
    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 비밀번호_변경_탈퇴된_사용자_400() {
    user.withdraw();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    ResponseStatusException ex =
        catchThrowableOfType(
            ResponseStatusException.class,
            () -> authService.changePassword(USER_ID, "anyPassword", "newPassword123"));

    assertThat(ex).isNotNull();
    assertThat(ex.getStatusCode().value()).isEqualTo(400);
    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 비밀번호_변경_OAuth_사용자_400() {
    User oauthUser = User.builder().email("oauth@example.com").provider("kakao").build();
    ReflectionTestUtils.setField(oauthUser, "id", USER_ID);
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(oauthUser));

    ResponseStatusException ex =
        catchThrowableOfType(
            ResponseStatusException.class,
            () -> authService.changePassword(USER_ID, "anyPassword", "newPassword123"));

    assertThat(ex).isNotNull();
    assertThat(ex.getStatusCode().value()).isEqualTo(400);
    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 비밀번호_변경_사용자_미존재_IllegalState() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.changePassword(USER_ID, "anyPassword", "newPassword123"))
        .isInstanceOf(IllegalStateException.class);

    verify(refreshTokenService, never()).delete(any());
  }

  @Test
  void 이메일_중복_확인_사용가능() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);

    boolean available = authService.checkEmailAvailability("new@example.com");

    assertThat(available).isTrue();
  }

  @Test
  void 이메일_중복_확인_이미_가입됨() {
    given(userRepository.existsByEmail("test@example.com")).willReturn(true);

    boolean available = authService.checkEmailAvailability("test@example.com");

    assertThat(available).isFalse();
  }
}
