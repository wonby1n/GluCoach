package com.ssafy.s309.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.auth.dto.LoginRequest;
import com.ssafy.s309.domain.auth.dto.SignupRequest;
import com.ssafy.s309.domain.auth.dto.TokenResponse;
import com.ssafy.s309.domain.auth.jwt.JwtProvider;
import com.ssafy.s309.domain.user.entity.Guardian;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayName("회원탈퇴 E2E 시나리오")
class AuthWithdrawFlowTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private AuthService authService;

  private static final UUID USER_ID = UUID.randomUUID();

  private User user;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .email("user@glucofit.com")
            .password("encodedPw")
            .provider("email")
            .height(175f)
            .weight(70f)
            .build();
    ReflectionTestUtils.setField(user, "userId", USER_ID);
  }

  @Test
  @DisplayName("회원가입 → 로그인 → 탈퇴 → 재로그인 불가 전체 플로우")
  void 전체_인증_플로우_검증() {
    // 1) 회원가입
    given(userRepository.existsByEmail("user@glucofit.com")).willReturn(false);
    given(passwordEncoder.encode("myPassword")).willReturn("encodedPw");
    given(userRepository.save(any(User.class)))
        .willAnswer(
            inv -> {
              User saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "userId", USER_ID);
              return saved;
            });
    given(jwtProvider.generateAccessToken(any(), anyString())).willReturn("at");
    given(jwtProvider.generateRefreshToken(any())).willReturn("rt");

    TokenResponse signupResp =
        authService.signup(new SignupRequest("user@glucofit.com", "myPassword"));
    assertThat(signupResp.accessToken()).isNotNull();

    // 2) 로그인 성공
    given(userRepository.findByEmailAndDeletedAtIsNull("user@glucofit.com"))
        .willReturn(Optional.of(user));
    given(passwordEncoder.matches("myPassword", "encodedPw")).willReturn(true);

    TokenResponse loginResp =
        authService.login(new LoginRequest("user@glucofit.com", "myPassword"));
    assertThat(loginResp.accessToken()).isNotNull();

    // 3) 회원 탈퇴
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    authService.withdraw(USER_ID, "myPassword");

    assertThat(user.isDeleted()).isTrue();
    assertThat(user.getEmail()).doesNotContain("glucofit");
    assertThat(user.getPassword()).isNull();
    assertThat(user.getHeight()).isNull();
    assertThat(user.getWeight()).isNull();
    verify(refreshTokenService).delete(USER_ID);

    // 4) 탈퇴 후 로그인 불가
    given(userRepository.findByEmailAndDeletedAtIsNull("user@glucofit.com"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("user@glucofit.com", "myPassword")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("탈퇴 시 보호자 정보도 함께 제거")
  void 탈퇴시_보호자_정보_클리어() {
    Guardian guardian =
        Guardian.builder()
            .user(user)
            .name("홍길동")
            .phone("01012345678")
            .relation("가족")
            .priority(0)
            .build();
    user.getGuardians().add(guardian);

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("myPassword", "encodedPw")).willReturn(true);

    authService.withdraw(USER_ID, "myPassword");

    assertThat(user.getGuardians()).isEmpty();
  }

  @Test
  @DisplayName("탈퇴 후 동일 이메일로 재가입 가능 (이메일 익명화됨)")
  void 탈퇴후_동일_이메일_재가입() {
    // 탈퇴 처리
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("myPassword", "encodedPw")).willReturn(true);

    authService.withdraw(USER_ID, "myPassword");

    // 이메일이 익명화되었으므로 원래 이메일은 더 이상 존재하지 않음
    assertThat(user.getEmail()).isNotEqualTo("user@glucofit.com");
    assertThat(user.getEmail()).matches("deleted_[a-f0-9-]+@withdrawn\\.local");

    // 재가입 시 이메일 중복 체크 통과
    given(userRepository.existsByEmail("user@glucofit.com")).willReturn(false);
    given(passwordEncoder.encode("newPassword")).willReturn("newEncodedPw");
    given(userRepository.save(any(User.class)))
        .willAnswer(
            inv -> {
              User saved = inv.getArgument(0);
              ReflectionTestUtils.setField(saved, "userId", UUID.randomUUID());
              return saved;
            });
    given(jwtProvider.generateAccessToken(any(), anyString())).willReturn("new-at");
    given(jwtProvider.generateRefreshToken(any())).willReturn("new-rt");

    TokenResponse reSignup =
        authService.signup(new SignupRequest("user@glucofit.com", "newPassword"));
    assertThat(reSignup.accessToken()).isEqualTo("new-at");
  }

  @Test
  @DisplayName("이중 탈퇴 시도 방지")
  void 이중_탈퇴_시도_예외() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("myPassword", "encodedPw")).willReturn(true);

    authService.withdraw(USER_ID, "myPassword");

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.withdraw(USER_ID, "myPassword"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 탈퇴한 사용자");
  }

  @Test
  @DisplayName("익명화 마스킹 규칙 검증")
  void 익명화_마스킹_규칙() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("myPassword", "encodedPw")).willReturn(true);

    authService.withdraw(USER_ID, "myPassword");

    // 이메일: deleted_{userId}@withdrawn.local
    assertThat(user.getEmail()).isEqualTo("deleted_" + USER_ID + "@withdrawn.local");
    // 비밀번호: null
    assertThat(user.getPassword()).isNull();
    // 신체정보: null
    assertThat(user.getHeight()).isNull();
    assertThat(user.getWeight()).isNull();
    // deletedAt: 설정됨
    assertThat(user.getDeletedAt()).isNotNull();
    // 설정값은 유지 (당뇨 타입 등은 통계용으로 보존)
    assertThat(user.getDiabetesType()).isNotNull();
  }
}
