package com.ssafy.s309.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.config.JpaConfig;
import com.ssafy.s309.domain.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class) // @EnableJpaAuditing 활성화 (created_at/updated_at 자동 채움)
@SuppressWarnings("NonAsciiCharacters") // 테스트 메소드명은 한글 사용
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Test
  void 이메일로_유저_조회() {
    // given
    User saved =
        userRepository.save(
            User.builder().email("find@example.com").name("테스트유저").phone("010-0000-0000").build());

    // when
    Optional<User> found = userRepository.findByEmail("find@example.com");

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(saved.getId());
  }

  @Test
  void 존재하지_않는_이메일은_empty() {
    // when
    Optional<User> found = userRepository.findByEmail("nobody@example.com");

    // then
    assertThat(found).isEmpty();
  }

  @Test
  void 이메일_중복_체크() {
    // given
    userRepository.save(
        User.builder().email("exists@example.com").name("테스트유저").phone("010-0000-0000").build());

    // when & then
    assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("none@example.com")).isFalse();
  }
}
