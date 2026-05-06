package com.ssafy.s309.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class) // @EnableJpaAuditing 활성화 (created_at/updated_at 자동 채움)
@SuppressWarnings("NonAsciiCharacters")
class UserEntityTest {

  @Autowired private TestEntityManager em;

  @Test
  void 유저_저장_및_기본값_확인() {
    User user =
        User.builder().email("test@example.com").name("테스트유저").phone("010-0000-0000").build();

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
