package com.ssafy.s309.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.auth.jwt.JwtProvider;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 비밀번호 변경 → RefreshToken 무효화 → 기존 토큰으로 reissue 실패 협력 검증.
 *
 * <p>실제 Redis/JWT 통합이 아닌 mock 기반으로 changePassword와 reissue 두 메서드의 협력 동작을 시뮬레이션. matches가 false를
 * 반환하면 AuthService.reissue가 "재사용 감지" 로직으로 빠지는 것이 정상 동작이며, 정책 변경 시 이 테스트도 함께 갱신되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayName("비밀번호 변경 후 RefreshToken 무효화 협력")
class AuthChangePasswordCollaborationTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtProvider jwtProvider;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private AuthService authService;

  private static final Integer USER_ID = 1;
  private static final String OLD_REFRESH_TOKEN = "old-refresh-token";

  private User user;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .email("user@glucofit.com")
            .password("oldEncodedPw")
            .provider("email")
            .build();
    ReflectionTestUtils.setField(user, "id", USER_ID);
  }

  @Test
  @DisplayName("비밀번호 변경 후 기존 RefreshToken으로 reissue 시도 시 실패")
  void 비밀번호_변경_후_기존_RefreshToken_재발급_실패() {
    // given: 비밀번호 변경 준비
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("oldRawPw", "oldEncodedPw")).willReturn(true);
    given(passwordEncoder.encode("newRawPw123")).willReturn("newEncodedPw");

    // when: 비밀번호 변경 수행
    authService.changePassword(USER_ID, "oldRawPw", "newRawPw123");

    // then: 새 해시 저장 검증
    assertThat(user.getPassword()).isEqualTo("newEncodedPw");

    // given: 기존 RefreshToken은 JWT 자체는 유효(서명/만료 OK)하지만 Redis에서는 삭제됨
    given(jwtProvider.validate(OLD_REFRESH_TOKEN)).willReturn(true);
    given(jwtProvider.isAccessToken(OLD_REFRESH_TOKEN)).willReturn(false);
    given(jwtProvider.getUserId(OLD_REFRESH_TOKEN)).willReturn(USER_ID);
    // RefreshToken이 무효화되었으므로 matches는 false
    given(refreshTokenService.matches(USER_ID, OLD_REFRESH_TOKEN)).willReturn(false);

    // when & then: 기존 토큰으로 reissue 실패
    assertThatThrownBy(() -> authService.reissue(OLD_REFRESH_TOKEN))
        .isInstanceOf(IllegalArgumentException.class);

    // 재사용 감지 경로 진입 검증: changePassword(1회) + reissue 재사용 감지(1회) = 2회 delete 호출.
    // reissue 정책이 "matches=false → 단순 401"로 바뀌면 호출 횟수가 1회로 줄어 이 테스트가 실패함 →
    // 정책 변경 시 의식적으로 갱신되도록 하는 가드.
    verify(refreshTokenService, times(2)).delete(USER_ID);
  }

  @Test
  @DisplayName("비밀번호 변경 후 새 로그인 RefreshToken은 정상 reissue 가능")
  void 변경_후_재로그인한_RefreshToken은_정상() {
    // given: 비밀번호 변경
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(passwordEncoder.matches("oldRawPw", "oldEncodedPw")).willReturn(true);
    given(passwordEncoder.encode("newRawPw123")).willReturn("newEncodedPw");

    authService.changePassword(USER_ID, "oldRawPw", "newRawPw123");
    verify(refreshTokenService).delete(USER_ID);

    // given: 재로그인으로 새 RefreshToken 발급된 상태 가정 (matches가 true 반환)
    String newRefreshToken = "new-refresh-after-relogin";
    given(jwtProvider.validate(newRefreshToken)).willReturn(true);
    given(jwtProvider.isAccessToken(newRefreshToken)).willReturn(false);
    given(jwtProvider.getUserId(newRefreshToken)).willReturn(USER_ID);
    given(refreshTokenService.matches(USER_ID, newRefreshToken)).willReturn(true);
    given(jwtProvider.generateAccessToken(USER_ID, "user@glucofit.com")).willReturn("at-2");
    given(jwtProvider.generateRefreshToken(USER_ID)).willReturn("rt-2");

    // when: reissue 호출
    var resp = authService.reissue(newRefreshToken);

    // then: 정상 발급
    assertThat(resp.accessToken()).isEqualTo("at-2");
    assertThat(resp.refreshToken()).isEqualTo("rt-2");
  }
}
