package com.ssafy.s309.domain.auth.controller;

import com.ssafy.s309.domain.auth.dto.EmailCheckResponse;
import com.ssafy.s309.domain.auth.dto.LoginRequest;
import com.ssafy.s309.domain.auth.dto.ReissueRequest;
import com.ssafy.s309.domain.auth.dto.SignupRequest;
import com.ssafy.s309.domain.auth.dto.TokenResponse;
import com.ssafy.s309.domain.auth.dto.WithdrawRequest;
import com.ssafy.s309.domain.auth.exception.RateLimitedException;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.auth.service.AuthService;
import com.ssafy.s309.domain.auth.service.EmailCheckRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  // jakarta.validation @Email 과 동일한 RFC 5322 유연 변형 (Hibernate Validator 기본)
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private final AuthService authService;
  private final EmailCheckRateLimiter emailCheckRateLimiter;

  @Operation(summary = "회원가입")
  @PostMapping("/signup")
  public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
  }

  @Operation(summary = "이메일 중복 확인", description = "사용 가능 여부 + 사유(status) 반환. 분당 IP당 10회 제한")
  @GetMapping("/email/check")
  public ResponseEntity<EmailCheckResponse> checkEmail(
      @RequestParam(value = "email", required = false) String email, HttpServletRequest request) {
    if (!isValidEmailFormat(email)) {
      return ResponseEntity.badRequest().body(EmailCheckResponse.ofInvalidFormat());
    }

    Integer retryAfter = emailCheckRateLimiter.tryAcquireOrGetRetryAfter(extractClientIp(request));
    if (retryAfter != null) {
      throw new RateLimitedException(retryAfter);
    }

    boolean available = authService.checkEmailAvailability(email);
    return ResponseEntity.ok(
        available ? EmailCheckResponse.ofAvailable() : EmailCheckResponse.ofAlreadyRegistered());
  }

  @Operation(summary = "로그인")
  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @Operation(summary = "토큰 재발급")
  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
    return ResponseEntity.ok(authService.reissue(request.refreshToken()));
  }

  @Operation(summary = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestBody ReissueRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "회원 탈퇴", description = "소프트 삭제 + 개인정보 익명화 처리")
  @DeleteMapping("/withdraw")
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestBody WithdrawRequest request) {
    authService.withdraw(principal.userId(), request.password());
    return ResponseEntity.noContent().build();
  }

  // ── 이메일 중복 확인 전용 예외 처리 ───────────────────────────────────

  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<EmailCheckResponse> handleRateLimited(RateLimitedException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
        .body(EmailCheckResponse.ofRateLimited(e.getRetryAfterSeconds()));
  }

  // ── 헬퍼 ────────────────────────────────────────────────────────────

  private static boolean isValidEmailFormat(String email) {
    return StringUtils.hasText(email) && EMAIL_PATTERN.matcher(email).matches();
  }

  /**
   * 클라이언트 실제 IP 추출.
   *
   * <p><strong>보안 가정</strong>: 본 메서드는 X-Forwarded-For 헤더를 가공 없이 신뢰한다. 따라서 반드시 신뢰 가능한 리버스 프록시(nginx
   * 등) 뒤에서 동작해야 안전하다. 애플리케이션이 외부에 직접 노출되면 공격자가 헤더를 조작해 IP 기반 rate limit 등을 우회할 수 있다.
   *
   * <p>현재 배포 토폴로지: 외부 → nginx(80/443) → backend(8080, 도커 내부망 only). backend 포트는 호스트로 노출되지 않음
   * (infra/docker-compose.yml 참고).
   *
   * <p>토폴로지 변경 시: server.forward-headers-strategy=NATIVE 글로벌 설정 또는 신뢰 프록시 IP 화이트리스트 도입 검토.
   */
  private String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
    if (StringUtils.hasText(forwarded)) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
