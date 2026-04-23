package com.ssafy.s309.domain.auth.service;

import com.ssafy.s309.domain.auth.jwt.JwtProperties;
import java.util.UUID;
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

  public void save(UUID userId, String refreshToken) {
    String key = KEY_PREFIX + userId;
    redisTemplate
        .opsForValue()
        .set(key, refreshToken, jwtProperties.getRefreshExpirationMs(), TimeUnit.MILLISECONDS);
  }

  public String find(UUID userId) {
    return redisTemplate.opsForValue().get(KEY_PREFIX + userId);
  }

  public void delete(UUID userId) {
    redisTemplate.delete(KEY_PREFIX + userId);
  }

  public boolean matches(UUID userId, String refreshToken) {
    String stored = find(userId);
    return refreshToken.equals(stored);
  }
}
