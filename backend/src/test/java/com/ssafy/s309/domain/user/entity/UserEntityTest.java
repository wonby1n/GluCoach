package com.ssafy.s309.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("NonAsciiCharacters") // 테스트 메소드명은 한글 사용
class UserEntityTest {

  @Autowired private TestEntityManager em;

  @Test
  void 유저_저장_및_기본값_확인() {
    // given
    User user = User.builder().email("test@example.com").build();

    // when
    em.persistAndFlush(user);
    em.clear();

    User found = em.find(User.class, user.getUserId());

    // then
    assertThat(found.getDiabetesType()).isEqualTo(DiabetesType.NONE);
    assertThat(found.getIsMedicated()).isFalse();
    assertThat(found.getNightWatch()).isFalse();
    assertThat(found.getTargetLow()).isEqualTo(70);
    assertThat(found.getTargetHigh()).isEqualTo(140);
  }
}
