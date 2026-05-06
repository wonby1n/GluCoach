package com.ssafy.s309.domain.auth.service;

import com.ssafy.s309.domain.auth.dto.LoginRequest;
import com.ssafy.s309.domain.auth.dto.SignupRequest;
import com.ssafy.s309.domain.auth.dto.TokenResponse;
import com.ssafy.s309.domain.auth.jwt.JwtProvider;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final JwtProvider jwtProvider;
  private final RefreshTokenService refreshTokenService;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public TokenResponse signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("이미 가입된 이메일입니다");
    }

    User user =
        User.builder()
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .provider("email")
            .name(request.name())
            .phone(request.phone())
            .build();

    userRepository.save(user);

    return issueTokens(user.getId(), user.getEmail());
  }

  public TokenResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmailAndDeletedAtIsNull(request.email())
            .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다"));

    if (user.getPassword() == null
        || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다");
    }

    return issueTokens(user.getId(), user.getEmail());
  }

  public TokenResponse reissue(String refreshToken) {
    if (!jwtProvider.validate(refreshToken) || jwtProvider.isAccessToken(refreshToken)) {
      throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다");
    }

    Integer userId = jwtProvider.getUserId(refreshToken);

    if (!refreshTokenService.matches(userId, refreshToken)) {
      refreshTokenService.delete(userId);
      log.warn("Refresh Token 재사용 감지 — 모든 토큰 무효화. userId={}", userId);
      throw new IllegalArgumentException("비정상적인 토큰 사용이 감지되었습니다. 다시 로그인해주세요");
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

    return issueTokens(user.getId(), user.getEmail());
  }

  public void logout(String refreshToken) {
    if (jwtProvider.validate(refreshToken)) {
      Integer userId = jwtProvider.getUserId(refreshToken);
      refreshTokenService.delete(userId);
    }
  }

  @Transactional
  public void withdraw(Integer userId, String password) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

    if (user.isDeleted()) {
      throw new IllegalArgumentException("이미 탈퇴한 사용자입니다");
    }

    if (user.getPassword() != null) {
      if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
        throw new IllegalArgumentException("비밀번호가 올바르지 않습니다");
      }
    }

    user.withdraw();
    refreshTokenService.delete(userId);
  }

  public boolean checkEmailAvailability(String email) {
    return !userRepository.existsByEmail(email);
  }

  @Transactional
  public void changePassword(Integer userId, String currentPassword, String newPassword) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("인증된 사용자를 DB에서 찾을 수 없습니다: " + userId));

    if (user.isDeleted() || user.getPassword() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 변경할 수 없는 계정입니다");
    }

    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다");
    }

    if (currentPassword.equals(newPassword)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 현재 비밀번호와 같습니다");
    }

    user.changePassword(passwordEncoder.encode(newPassword));
    refreshTokenService.delete(userId);
  }

  private TokenResponse issueTokens(Integer userId, String email) {
    String accessToken = jwtProvider.generateAccessToken(userId, email);
    String refreshToken = jwtProvider.generateRefreshToken(userId);

    refreshTokenService.save(userId, refreshToken);

    return new TokenResponse(accessToken, refreshToken);
  }
}
