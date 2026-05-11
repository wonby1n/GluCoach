package com.ssafy.s309.domain.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh Token 같은 민감 토큰의 저장소 보관용 해시 유틸.
 *
 * <p>JWT 서명과는 별개로 Redis 등 저장소가 침해되더라도 원본 토큰이 그대로 노출되지 않도록 SHA-256 다이제스트를 보관한다. JWT 자체의 엔트로피가 충분히
 * 크므로 salt 는 사용하지 않는다 — 동일 입력은 동일 해시이며, 그 비교만으로 검증 가능.
 */
public final class TokenHasher {

  private TokenHasher() {}

  public static String sha256(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
    }
  }
}
