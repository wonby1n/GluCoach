package com.ssafy.s309.domain.auth.jwt;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JwtProperties {

  private final String secret;
  private final long accessExpirationMs;
  private final long refreshExpirationMs;
  private final String issuer;

  public JwtProperties(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-expiration-ms}") long accessExpirationMs,
      @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs,
      @Value("${jwt.issuer}") String issuer) {
    this.secret = secret;
    this.accessExpirationMs = accessExpirationMs;
    this.refreshExpirationMs = refreshExpirationMs;
    this.issuer = issuer;
  }
}
