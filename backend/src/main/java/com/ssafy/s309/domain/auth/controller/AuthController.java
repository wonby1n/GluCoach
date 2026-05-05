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
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Auth", description = "인증 관련 API")
public class AuthController {

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

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
      @RequestParam("email") @NotBlank @Email String email, HttpServletRequest request) {
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
  // 이 컨트롤러의 다른 메서드들은 @RequestBody @Valid 만 쓰므로 MethodArgumentNotValidException
  // 만 던지고, ConstraintViolationException 은 /email/check 에서만 발생한다.

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<EmailCheckResponse> handleEmailValidation(ConstraintViolationException e) {
    return ResponseEntity.badRequest().body(EmailCheckResponse.ofInvalidFormat());
  }

  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<EmailCheckResponse> handleRateLimited(RateLimitedException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
        .body(EmailCheckResponse.ofRateLimited(e.getRetryAfterSeconds()));
  }

  // ── 헬퍼 ────────────────────────────────────────────────────────────

  /** 클라이언트 실제 IP 추출. nginx 등 리버스 프록시 뒤에 있을 때 X-Forwarded-For 의 첫 값을 우선 사용. */
  private String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
    if (StringUtils.hasText(forwarded)) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
