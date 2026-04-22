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
import com.ssafy.s309.domain.user.entity.Guardian;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.GuardianRepository;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  @Mock private GuardianRepository guardianRepository;
  @InjectMocks private UserService userService;

  // @GeneratedValue(UUID) 는 DB 없이 null → 테스트용 UUID를 별도로 관리
  private static final UUID USER_ID = UUID.randomUUID();

  private User user;

  @BeforeEach
  void setUp() {
    user = User.builder().email("test@example.com").build();
    // @GeneratedValue 는 DB 없이 null이므로 테스트용 UUID를 주입해서 소유권 비교를 정상화
    ReflectionTestUtils.setField(user, "userId", USER_ID);
  }

  // ── Settings ──────────────────────────────────────────────

  @Test
  void 설정_조회_성공() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    SettingsResponse response = userService.getSettings(USER_ID);

    assertThat(response.userId()).isEqualTo(user.getUserId());
    assertThat(response.diabetesType()).isEqualTo(DiabetesType.NONE);
    assertThat(response.targetLow()).isEqualTo(70);
    assertThat(response.targetHigh()).isEqualTo(140);
  }

  @Test
  void 설정_수정_성공() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    SettingsUpdateRequest request =
        new SettingsUpdateRequest(
            170f, 65f, DiabetesType.TYPE2, true, 80, 160, 75, 200, true, "BASIC");

    SettingsResponse response = userService.updateSettings(USER_ID, request);

    assertThat(response.height()).isEqualTo(170f);
    assertThat(response.diabetesType()).isEqualTo(DiabetesType.TYPE2);
    assertThat(response.isMedicated()).isTrue();
  }

  @Test
  void 설정_수정_null_필드는_기존값_유지() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

    // height만 업데이트, 나머지 null
    SettingsUpdateRequest request =
        new SettingsUpdateRequest(175f, null, null, null, null, null, null, null, null, null);

    SettingsResponse response = userService.updateSettings(USER_ID, request);

    assertThat(response.height()).isEqualTo(175f);
    assertThat(response.targetLow()).isEqualTo(70); // 기본값 유지
  }

  @Test
  void 존재하지_않는_유저_설정_조회_예외() {
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getSettings(USER_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 유저");
  }

  // ── Guardian ──────────────────────────────────────────────

  @Test
  void 보호자_목록_조회_priority_순서_반환() {
    Guardian g1 = Guardian.builder().user(user).name("첫째").phone("01011111111").priority(0).build();
    Guardian g2 = Guardian.builder().user(user).name("둘째").phone("01022222222").priority(1).build();
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(USER_ID))
        .willReturn(List.of(g1, g2));

    List<GuardianResponse> result = userService.getGuardians(USER_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("첫째");
    assertThat(result.get(0).priority()).isEqualTo(0);
    assertThat(result.get(1).name()).isEqualTo("둘째");
    assertThat(result.get(1).priority()).isEqualTo(1);
  }

  @Test
  void 보호자_등록_priority_자동_할당() {
    GuardianRequest request = new GuardianRequest("엄마", "01012345678", "가족", false);
    Guardian saved =
        Guardian.builder().user(user).name("엄마").phone("01012345678").priority(2).build();

    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    given(guardianRepository.countByUser_UserId(USER_ID)).willReturn(2);
    given(guardianRepository.save(any(Guardian.class))).willReturn(saved);

    GuardianResponse response = userService.createGuardian(USER_ID, request);

    assertThat(response.name()).isEqualTo("엄마");
    assertThat(response.priority()).isEqualTo(2);
  }

  @Test
  void 보호자_수정_성공() {
    UUID guardianId = UUID.randomUUID();
    Guardian guardian =
        Guardian.builder().user(user).name("아빠").phone("01011111111").priority(0).build();
    GuardianRequest request = new GuardianRequest("아버지", "01099999999", "부모", true);

    given(guardianRepository.findById(any(UUID.class))).willReturn(Optional.of(guardian));

    GuardianResponse response = userService.updateGuardian(USER_ID, guardianId, request);

    assertThat(response.name()).isEqualTo("아버지");
    assertThat(response.phone()).isEqualTo("01099999999");
    assertThat(response.isPrimary()).isTrue();
  }

  @Test
  void 다른_유저의_보호자_수정_예외() {
    UUID guardianId = UUID.randomUUID();
    User otherUser = User.builder().email("other@example.com").build();
    // otherUser의 guardianId → OTHER_USER_ID와 매칭되도록 Guardian 생성
    // USER_ID와 OTHER_USER_ID는 다른 값이므로 소유권 검증 실패
    Guardian guardian =
        Guardian.builder().user(otherUser).name("타인").phone("01099999999").priority(0).build();
    GuardianRequest request = new GuardianRequest("수정시도", "01000000000", null, null);

    given(guardianRepository.findById(any(UUID.class))).willReturn(Optional.of(guardian));

    // guardian.getUser().getUserId() = null, USER_ID ≠ null → 예외 발생
    assertThatThrownBy(() -> userService.updateGuardian(USER_ID, guardianId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해당 유저의 보호자가 아닙니다");
  }

  @Test
  void 보호자_삭제_후_priority_재정렬() {
    UUID deletedId = UUID.randomUUID();
    Guardian g0 = Guardian.builder().user(user).name("첫째").phone("01011111111").priority(0).build();
    Guardian g1 = Guardian.builder().user(user).name("둘째").phone("01022222222").priority(1).build();
    Guardian g2 = Guardian.builder().user(user).name("셋째").phone("01033333333").priority(2).build();

    given(guardianRepository.findById(any(UUID.class))).willReturn(Optional.of(g1));
    given(guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(USER_ID))
        .willReturn(List.of(g0, g2));

    userService.deleteGuardian(USER_ID, deletedId);

    verify(guardianRepository).delete(g1);
    assertThat(g2.getPriority()).isEqualTo(1); // 2 → 1로 당겨짐
    assertThat(g0.getPriority()).isEqualTo(0); // 변화 없음
  }
}
