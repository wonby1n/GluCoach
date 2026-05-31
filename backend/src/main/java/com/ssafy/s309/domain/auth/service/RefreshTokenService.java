package com.ssafy.s309.domain.auth.service;

import com.ssafy.s309.domain.auth.jwt.JwtProperties;
import com.ssafy.s309.domain.auth.jwt.TokenHasher;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final String KEY_PREFIX = "refresh:";

  private final StringRedisTemplate redisTemplate;
  private final JwtProperties jwtProperties;

  /** 클라이언트에 발급한 원본 토큰의 SHA-256 해시를 Redis 에 저장. 원본은 클라이언트에만 존재. */
  public void save(Integer userId, String refreshToken) {
    String key = KEY_PREFIX + userId;
    redisTemplate
        .opsForValue()
        .set(
            key,
            TokenHasher.sha256(refreshToken),
            jwtProperties.getRefreshExpirationMs(),
            TimeUnit.MILLISECONDS);
  }

  public void delete(Integer userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }

  /**
   * 클라이언트가 보낸 raw 토큰을 SHA-256 해시 후 Redis 의 저장값과 비교.
   *
   * <p>저장값 없음(null) 시 false — reissue 흐름에서 "재사용 감지" 로 이어진다.
   */
  public boolean matches(Integer userId, String refreshToken) {
    String stored = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
    if (stored == null) {
      return false;
    }
    return TokenHasher.sha256(refreshToken).equals(stored);
  }
}
