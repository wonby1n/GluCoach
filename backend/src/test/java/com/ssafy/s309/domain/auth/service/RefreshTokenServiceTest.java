package com.ssafy.s309.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.auth.jwt.JwtProperties;
import com.ssafy.s309.domain.auth.jwt.TokenHasher;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class RefreshTokenServiceTest {

  private static final Integer USER_ID = 1;
  private static final String RAW_TOKEN = "eyJhbGciOiJIUzI1NiJ9.payload.signature";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;
  @Mock private JwtProperties jwtProperties;
  @InjectMocks private RefreshTokenService service;

  @Test
  void save_는_raw_토큰이_아닌_SHA256_해시를_Redis에_저장한다() {
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    given(jwtProperties.getRefreshExpirationMs()).willReturn(3600000L);

    service.save(USER_ID, RAW_TOKEN);

    String expectedHash = TokenHasher.sha256(RAW_TOKEN);
    verify(valueOps)
        .set(eq("refresh:1"), eq(expectedHash), eq(3600000L), eq(TimeUnit.MILLISECONDS));
    // 저장값이 raw 토큰과 다름을 명시적으로 보장
    assertThat(expectedHash).isNotEqualTo(RAW_TOKEN).hasSize(64);
  }

  @Test
  void matches_는_클라이언트_토큰을_해시한_뒤_저장값과_같으면_true를_반환한다() {
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    given(valueOps.get("refresh:1")).willReturn(TokenHasher.sha256(RAW_TOKEN));

    assertThat(service.matches(USER_ID, RAW_TOKEN)).isTrue();
  }

  @Test
  void matches_는_저장값이_다른_해시면_false를_반환한다() {
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    given(valueOps.get("refresh:1")).willReturn(TokenHasher.sha256("different-token"));

    assertThat(service.matches(USER_ID, RAW_TOKEN)).isFalse();
  }

  @Test
  void matches_는_저장값이_null이면_false를_반환한다() {
    given(redisTemplate.opsForValue()).willReturn(valueOps);
    given(valueOps.get("refresh:1")).willReturn(null);

    assertThat(service.matches(USER_ID, RAW_TOKEN)).isFalse();
  }

  @Test
  void delete_는_userId_기반_키를_삭제한다() {
    service.delete(USER_ID);
    verify(redisTemplate).delete("refresh:1");
  }
}
