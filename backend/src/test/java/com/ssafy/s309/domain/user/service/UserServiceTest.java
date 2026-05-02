package com.ssafy.s309.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.user.dto.GuardianRequest;
import com.ssafy.s309.domain.user.dto.GuardianResponse;
import com.ssafy.s309.domain.user.dto.SettingsResponse;
import com.ssafy.s309.domain.user.dto.SettingsUpdateRequest;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.entity.WardGuardian;
import com.ssafy.s309.domain.user.repository.UserRepository;
import com.ssafy.s309.domain.user.repository.WardGuardianRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private WardGuardianRepository wardGuardianRepository;
  @InjectMocks private UserService userService;

  private static final Integer USER_ID = 1;

  private User ward;

  @BeforeEach
  void setUp() {
    ward = User.builder().email("test@example.com").build();
    ReflectionTestUtils.setField(ward, "id", USER_ID);
  }

  // ── Settings ──────────────────────────────────────────────

  @Test
  void 설정_조회_성공() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(ward));

    SettingsResponse response = userService.getSettings(USER_ID);

    assertThat(response.userId()).isEqualTo(USER_ID);
    assertThat(response.diabetesType()).isNull();
    assertThat(response.weekStartDay()).isEqualTo(1);
  }

  @Test
  void 설정_수정_성공() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(ward));

    SettingsUpdateRequest request =
        new SettingsUpdateRequest(
            "홍길동", 30, "male", "01012345678", 170f, 65f, DiabetesType.T2D, true, 80, 160, 1);

    SettingsResponse response = userService.updateSettings(USER_ID, request);

    assertThat(response.height()).isEqualTo(170f);
    assertThat(response.diabetesType()).isEqualTo(DiabetesType.T2D);
    assertThat(response.isMedicated()).isTrue();
    assertThat(response.name()).isEqualTo("홍길동");
  }

  @Test
  void 설정_수정_null_필드는_기존값_유지() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(ward));

    SettingsUpdateRequest request =
        new SettingsUpdateRequest(null, null, null, null, 175f, null, null, null, null, null, null);

    SettingsResponse response = userService.updateSettings(USER_ID, request);

    assertThat(response.height()).isEqualTo(175f);
    assertThat(response.weekStartDay()).isEqualTo(1);
  }

  @Test
  void 존재하지_않는_유저_설정_조회_예외() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getSettings(USER_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 유저");
  }

  // ── Guardian (WardGuardian) ────────────────────────────────

  @Test
  void 보호자_목록_조회_priority_순서_반환() {
    User g1 = User.builder().email("g1@example.com").build();
    User g2 = User.builder().email("g2@example.com").build();
    ReflectionTestUtils.setField(g1, "id", 2);
    ReflectionTestUtils.setField(g2, "id", 3);

    WardGuardian wg1 =
        WardGuardian.builder().ward(ward).guardian(g1).relation("부모").priority(0).build();
    WardGuardian wg2 =
        WardGuardian.builder().ward(ward).guardian(g2).relation("배우자").priority(1).build();
    ReflectionTestUtils.setField(wg1, "id", 10);
    ReflectionTestUtils.setField(wg2, "id", 11);

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(ward));
    given(wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(USER_ID))
        .willReturn(List.of(wg1, wg2));

    List<GuardianResponse> result = userService.getGuardians(USER_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).priority()).isEqualTo(0);
    assertThat(result.get(0).relation()).isEqualTo("부모");
    assertThat(result.get(1).priority()).isEqualTo(1);
  }

  @Test
  void 보호자_등록_priority_자동_할당() {
    User guardian = User.builder().email("guardian@example.com").build();
    ReflectionTestUtils.setField(guardian, "id", 2);

    WardGuardian saved =
        WardGuardian.builder().ward(ward).guardian(guardian).relation("부모").priority(2).build();
    ReflectionTestUtils.setField(saved, "id", 10);

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(ward));
    given(userRepository.findById(2)).willReturn(Optional.of(guardian));
    given(wardGuardianRepository.countByWard_Id(USER_ID)).willReturn(2);
    given(wardGuardianRepository.save(any(WardGuardian.class))).willReturn(saved);

    GuardianResponse response = userService.createGuardian(USER_ID, new GuardianRequest(2, "부모"));

    assertThat(response.guardianId()).isEqualTo(2);
    assertThat(response.priority()).isEqualTo(2);
  }

  @Test
  void 보호자_수정_성공() {
    User guardian = User.builder().email("guardian@example.com").build();
    ReflectionTestUtils.setField(guardian, "id", 2);

    WardGuardian wg =
        WardGuardian.builder().ward(ward).guardian(guardian).relation("부모").priority(0).build();
    ReflectionTestUtils.setField(wg, "id", 10);

    given(wardGuardianRepository.findById(10)).willReturn(Optional.of(wg));

    GuardianResponse response =
        userService.updateGuardian(USER_ID, 10, new GuardianRequest(2, "가족"));

    assertThat(response.relation()).isEqualTo("가족");
  }

  @Test
  void 다른_유저의_보호자_수정_예외() {
    User otherWard = User.builder().email("other@example.com").build();
    ReflectionTestUtils.setField(otherWard, "id", 99);

    User guardian = User.builder().email("guardian@example.com").build();
    ReflectionTestUtils.setField(guardian, "id", 2);

    WardGuardian wg = WardGuardian.builder().ward(otherWard).guardian(guardian).priority(0).build();
    ReflectionTestUtils.setField(wg, "id", 10);

    given(wardGuardianRepository.findById(10)).willReturn(Optional.of(wg));

    assertThatThrownBy(() -> userService.updateGuardian(USER_ID, 10, new GuardianRequest(2, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해당 유저의 보호자가 아닙니다");
  }

  @Test
  void 보호자_삭제_후_priority_재정렬() {
    User g1 = User.builder().email("g1@example.com").build();
    User g2 = User.builder().email("g2@example.com").build();
    User g3 = User.builder().email("g3@example.com").build();
    ReflectionTestUtils.setField(g1, "id", 2);
    ReflectionTestUtils.setField(g2, "id", 3);
    ReflectionTestUtils.setField(g3, "id", 4);

    WardGuardian wg0 = WardGuardian.builder().ward(ward).guardian(g1).priority(0).build();
    WardGuardian wg1 = WardGuardian.builder().ward(ward).guardian(g2).priority(1).build();
    WardGuardian wg2 = WardGuardian.builder().ward(ward).guardian(g3).priority(2).build();
    ReflectionTestUtils.setField(wg1, "id", 11);

    given(wardGuardianRepository.findById(11)).willReturn(Optional.of(wg1));
    given(wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(USER_ID))
        .willReturn(List.of(wg0, wg2));

    userService.deleteGuardian(USER_ID, 11);

    verify(wardGuardianRepository).delete(wg1);
    assertThat(wg2.getPriority()).isEqualTo(1);
    assertThat(wg0.getPriority()).isEqualTo(0);
  }
}
