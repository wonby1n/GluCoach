package com.ssafy.s309.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class EmailCheckRateLimiterTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private EmailCheckRateLimiter rateLimiter;

  private static final String IP = "127.0.0.1";
  private static final String KEY = "rate:email-check:" + IP;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
  }

  @Test
  void 첫_호출_허용_및_TTL_설정() {
    given(valueOperations.increment(KEY)).willReturn(1L);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isNull();
    verify(redisTemplate).expire(eq(KEY), eq(60L), eq(TimeUnit.SECONDS));
  }

  @Test
  void 한도_내_호출_허용_TTL_재설정_없음() {
    given(valueOperations.increment(KEY)).willReturn(5L);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isNull();
    verify(redisTemplate, never()).expire(any(), anyLong(), any());
  }

  @Test
  void 한도_경계값_10회는_허용() {
    given(valueOperations.increment(KEY)).willReturn(10L);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isNull();
  }

  @Test
  void 한도_초과_차단_및_남은_TTL_반환() {
    given(valueOperations.increment(KEY)).willReturn(11L);
    given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(42L);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isEqualTo(42);
  }

  @Test
  void TTL_조회_실패시_기본_윈도우_반환() {
    given(valueOperations.increment(KEY)).willReturn(11L);
    given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(-1L);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isEqualTo(60);
  }

  @Test
  void TTL_null_반환시_기본_윈도우_반환() {
    given(valueOperations.increment(KEY)).willReturn(11L);
    given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(null);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isEqualTo(60);
  }

  @Test
  void Redis_장애시_fail_open() {
    given(valueOperations.increment(KEY))
        .willThrow(new RedisConnectionFailureException("Redis down"));

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isNull();
  }

  @Test
  void increment_null_반환시_허용() {
    given(valueOperations.increment(KEY)).willReturn(null);

    Integer retryAfter = rateLimiter.tryAcquireOrGetRetryAfter(IP);

    assertThat(retryAfter).isNull();
  }
}
