package com.ssafy.s309.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class TokenHasherTest {

  @Test
  void sha256_은_동일_입력에_동일_해시를_반환한다() {
    String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
    assertThat(TokenHasher.sha256(token)).isEqualTo(TokenHasher.sha256(token));
  }

  @Test
  void sha256_은_64자_소문자_hex_문자열을_반환한다() {
    String hash = TokenHasher.sha256("any-token");
    assertThat(hash).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void sha256_은_다른_입력에_다른_해시를_반환한다() {
    assertThat(TokenHasher.sha256("token-a")).isNotEqualTo(TokenHasher.sha256("token-b"));
  }

  @Test
  void sha256_결과는_원본_토큰을_포함하지_않는다() {
    String token = "secret-refresh-token";
    assertThat(TokenHasher.sha256(token)).doesNotContain(token);
  }
}
