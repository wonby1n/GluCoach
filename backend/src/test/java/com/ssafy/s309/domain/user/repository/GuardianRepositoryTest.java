package com.ssafy.s309.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.domain.user.entity.Guardian;
import com.ssafy.s309.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SuppressWarnings("NonAsciiCharacters")
class GuardianRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private GuardianRepository guardianRepository;

  private User user;

  @BeforeEach
  void setUp() {
    user = em.persistAndFlush(User.builder().email("guardian-test@example.com").build());
  }

  @Test
  void 보호자_priority_오름차순_정렬() {
    // given
    em.persistAndFlush(
        Guardian.builder().user(user).name("셋째").phone("01033333333").priority(2).build());
    em.persistAndFlush(
        Guardian.builder().user(user).name("첫째").phone("01011111111").priority(0).build());
    em.persistAndFlush(
        Guardian.builder().user(user).name("둘째").phone("01022222222").priority(1).build());
    em.clear();

    // when
    List<Guardian> result =
        guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(user.getUserId());

    // then
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getName()).isEqualTo("첫째");
    assertThat(result.get(1).getName()).isEqualTo("둘째");
    assertThat(result.get(2).getName()).isEqualTo("셋째");
  }

  @Test
  void 보호자_수_카운트() {
    // given
    em.persistAndFlush(
        Guardian.builder().user(user).name("A").phone("01011111111").priority(0).build());
    em.persistAndFlush(
        Guardian.builder().user(user).name("B").phone("01022222222").priority(1).build());

    // when
    int count = guardianRepository.countByUser_UserId(user.getUserId());

    // then
    assertThat(count).isEqualTo(2);
  }

  @Test
  void 다른_유저의_보호자는_조회되지_않음() {
    // given
    User other = em.persistAndFlush(User.builder().email("other@example.com").build());
    em.persistAndFlush(
        Guardian.builder().user(user).name("내 보호자").phone("01011111111").priority(0).build());
    em.persistAndFlush(
        Guardian.builder().user(other).name("다른 보호자").phone("01099999999").priority(0).build());
    em.clear();

    // when
    List<Guardian> result =
        guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(user.getUserId());

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("내 보호자");
  }
}
