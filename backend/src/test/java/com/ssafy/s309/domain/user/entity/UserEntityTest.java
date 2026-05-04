package com.ssafy.s309.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("NonAsciiCharacters")
class UserEntityTest {

  @Autowired private TestEntityManager em;

  @Test
  void 유저_저장_및_기본값_확인() {
    User user = User.builder().email("test@example.com").build();

    em.persistAndFlush(user);
    em.clear();

    User found = em.find(User.class, user.getId());

    assertThat(found.getDiabetesType()).isNull();
    assertThat(found.getIsMedicated()).isNull();
    assertThat(found.getWeekStartDay()).isEqualTo((short) 1);
    assertThat(found.getTargetLow()).isNull();
    assertThat(found.getTargetHigh()).isNull();
  }
}
