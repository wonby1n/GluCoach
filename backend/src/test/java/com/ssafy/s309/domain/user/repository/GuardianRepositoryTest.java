package com.ssafy.s309.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.config.JpaConfig;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.entity.WardGuardian;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class GuardianRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private WardGuardianRepository wardGuardianRepository;

  private User ward;
  private User g1User;
  private User g2User;
  private User g3User;

  @BeforeEach
  void setUp() {
    ward = em.persistAndFlush(buildUser("ward@example.com"));
    g1User = em.persistAndFlush(buildUser("g1@example.com"));
    g2User = em.persistAndFlush(buildUser("g2@example.com"));
    g3User = em.persistAndFlush(buildUser("g3@example.com"));
  }

  private static User buildUser(String email) {
    return User.builder().email(email).name("테스트유저").phone("010-0000-0000").build();
  }

  @Test
  void 보호자_priority_오름차순_정렬() {
    em.persistAndFlush(
        WardGuardian.builder()
            .ward(ward)
            .guardian(g3User)
            .relation("친척")
            .priority((short) 2)
            .build());
    em.persistAndFlush(
        WardGuardian.builder()
            .ward(ward)
            .guardian(g1User)
            .relation("부모")
            .priority((short) 0)
            .build());
    em.persistAndFlush(
        WardGuardian.builder()
            .ward(ward)
            .guardian(g2User)
            .relation("배우자")
            .priority((short) 1)
            .build());
    em.clear();

    List<WardGuardian> result =
        wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(ward.getId());

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getPriority()).isEqualTo((short) 0);
    assertThat(result.get(1).getPriority()).isEqualTo((short) 1);
    assertThat(result.get(2).getPriority()).isEqualTo((short) 2);
  }

  @Test
  void 보호자_수_카운트() {
    em.persistAndFlush(
        WardGuardian.builder().ward(ward).guardian(g1User).priority((short) 0).build());
    em.persistAndFlush(
        WardGuardian.builder().ward(ward).guardian(g2User).priority((short) 1).build());

    int count = wardGuardianRepository.countByWard_Id(ward.getId());

    assertThat(count).isEqualTo(2);
  }

  @Test
  void 다른_유저의_보호자는_조회되지_않음() {
    User otherWard = em.persistAndFlush(buildUser("other@example.com"));
    em.persistAndFlush(
        WardGuardian.builder().ward(ward).guardian(g1User).priority((short) 0).build());
    em.persistAndFlush(
        WardGuardian.builder().ward(otherWard).guardian(g2User).priority((short) 0).build());
    em.clear();

    List<WardGuardian> result =
        wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(ward.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getGuardian().getId()).isEqualTo(g1User.getId());
  }
}
