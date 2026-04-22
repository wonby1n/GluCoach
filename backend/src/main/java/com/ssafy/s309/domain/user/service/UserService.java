package com.ssafy.s309.domain.user.service;

import com.ssafy.s309.domain.user.dto.GuardianRequest;
import com.ssafy.s309.domain.user.dto.GuardianResponse;
import com.ssafy.s309.domain.user.dto.SettingsResponse;
import com.ssafy.s309.domain.user.dto.SettingsUpdateRequest;
import com.ssafy.s309.domain.user.entity.Guardian;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.GuardianRepository;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final GuardianRepository guardianRepository;

  // ── Settings ──────────────────────────────────────────────

  public SettingsResponse getSettings(UUID userId) {
    User user = findUserById(userId);
    return SettingsResponse.from(user);
  }

  @Transactional
  public SettingsResponse updateSettings(UUID userId, SettingsUpdateRequest request) {
    User user = findUserById(userId);
    user.updateSettings(
        request.height(),
        request.weight(),
        request.diabetesType(),
        request.isMedicated(),
        request.targetLow(),
        request.targetHigh(),
        request.alertLow(),
        request.alertHigh(),
        request.nightWatch(),
        request.characterType());
    return SettingsResponse.from(user);
  }

  // ── Guardian ──────────────────────────────────────────────

  public List<GuardianResponse> getGuardians(UUID userId) {
    findUserById(userId);
    return guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(userId).stream()
        .map(GuardianResponse::from)
        .toList();
  }

  @Transactional
  public GuardianResponse createGuardian(UUID userId, GuardianRequest request) {
    User user = findUserById(userId);
    int nextPriority = guardianRepository.countByUser_UserId(userId);
    Guardian guardian =
        Guardian.builder()
            .user(user)
            .name(request.name())
            .phone(request.phone())
            .relation(request.relation())
            .isPrimary(request.isPrimary())
            .priority(nextPriority)
            .build();
    return GuardianResponse.from(guardianRepository.save(guardian));
  }

  @Transactional
  public GuardianResponse updateGuardian(UUID userId, UUID guardianId, GuardianRequest request) {
    Guardian guardian = findGuardianByIdAndUserId(guardianId, userId);
    guardian.update(request.name(), request.phone(), request.relation(), request.isPrimary());
    return GuardianResponse.from(guardian);
  }

  @Transactional
  public void deleteGuardian(UUID userId, UUID guardianId) {
    Guardian guardian = findGuardianByIdAndUserId(guardianId, userId);
    int deletedPriority = guardian.getPriority();
    guardianRepository.delete(guardian);
    guardianRepository.flush();

    // 삭제된 priority 이후 항목들을 한 칸씩 당김
    List<Guardian> remaining = guardianRepository.findAllByUser_UserIdOrderByPriorityAsc(userId);
    remaining.stream()
        .filter(g -> g.getPriority() > deletedPriority)
        .forEach(g -> g.updatePriority(g.getPriority() - 1));
  }

  // ── 내부 헬퍼 ─────────────────────────────────────────────

  private User findUserById(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));
  }

  private Guardian findGuardianByIdAndUserId(UUID guardianId, UUID userId) {
    Guardian guardian =
        guardianRepository
            .findById(guardianId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보호자입니다: " + guardianId));
    if (!Objects.equals(guardian.getUser().getUserId(), userId)) {
      throw new IllegalArgumentException("해당 유저의 보호자가 아닙니다");
    }
    return guardian;
  }
}
