package com.ssafy.s309.domain.auth.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 중복 확인 엔드포인트의 per-IP rate limiter.
 *
 * <p>Redis INCR + EXPIRE 기반의 fixed-window. 사용자 열거(enumeration) 공격 완화 목적으로 60초 창에 IP당 10회로 제한한다.
 *
 * <p>Redis 일시 장애 시에는 fail-open (요청을 통과시킴) — 가용성을 우선.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailCheckRateLimiter {

  private static final String KEY_PREFIX = "rate:email-check:";
  private static final int MAX_REQUESTS_PER_WINDOW = 10;
  private static final long WINDOW_SECONDS = 60;

  private final StringRedisTemplate redisTemplate;

  /**
   * 호출 가능 여부 판정. 차단 시 남은 TTL(초)을 반환, 허용 시 null.
   *
   * @param ip 클라이언트 IP
   * @return 차단되면 retry-after 초, 허용이면 null
   */
  public Integer tryAcquireOrGetRetryAfter(String ip) {
    String key = KEY_PREFIX + ip;

    Long count;
    try {
      count = redisTemplate.opsForValue().increment(key);
    } catch (Exception e) {
      log.warn("Redis 장애로 rate limit fail-open: ip={}, error={}", ip, e.getMessage());
      return null;
    }

    if (count == null) {
      return null;
    }

    if (count == 1L) {
      redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    if (count > MAX_REQUESTS_PER_WINDOW) {
      Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
      return (ttl != null && ttl > 0) ? ttl.intValue() : (int) WINDOW_SECONDS;
    }

    return null;
  }
}
