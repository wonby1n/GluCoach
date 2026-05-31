package com.ssafy.s309.domain.auth.jwt;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_TOKEN_TYPE = "type";
  private static final String TOKEN_TYPE_ACCESS = "access";
  private static final String TOKEN_TYPE_REFRESH = "refresh";

  private final SecretKey secretKey;
  private final JwtProperties properties;

  public JwtProvider(JwtProperties properties) {
    this.properties = properties;
    byte[] keyBytes = toKeyBytes(properties.getSecret());
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
  }

  public String generateAccessToken(Integer userId, String email) {
    return buildToken(userId, email, TOKEN_TYPE_ACCESS, properties.getAccessExpirationMs());
  }

  public String generateRefreshToken(Integer userId) {
    return buildToken(userId, null, TOKEN_TYPE_REFRESH, properties.getRefreshExpirationMs());
  }

  public boolean validate(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (ExpiredJwtException e) {
      log.debug("만료된 JWT: {}", e.getMessage());
    } catch (UnsupportedJwtException | MalformedJwtException | SignatureException e) {
      log.debug("잘못된 JWT: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.debug("빈 JWT: {}", e.getMessage());
    }
    return false;
  }

  public CustomUserPrincipal toPrincipal(String token) {
    Claims claims = parseClaims(token);
    Integer userId = Integer.parseInt(claims.getSubject());
    String email = claims.get(CLAIM_EMAIL, String.class);
    return new CustomUserPrincipal(userId, email);
  }

  public boolean isAccessToken(String token) {
    return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class));
  }

  public Integer getUserId(String token) {
    return Integer.parseInt(parseClaims(token).getSubject());
  }

  // ── 내부 헬퍼 ─────────────────────────────────────────────

  private String buildToken(Integer userId, String email, String tokenType, long expirationMs) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);

    var builder =
        Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .issuer(properties.getIssuer())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey, Jwts.SIG.HS256);

    if (email != null) {
      builder.claim(CLAIM_EMAIL, email);
    }

    return builder.compact();
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  private static byte[] toKeyBytes(String secret) {
    try {
      return Decoders.BASE64.decode(secret);
    } catch (IllegalArgumentException e) {
      return secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  public static String generateBase64Secret(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
