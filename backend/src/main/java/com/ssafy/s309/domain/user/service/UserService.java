package com.ssafy.s309.domain.user.service;

import com.ssafy.s309.domain.user.dto.GuardianRequest;
import com.ssafy.s309.domain.user.dto.GuardianResponse;
import com.ssafy.s309.domain.user.dto.SettingsResponse;
import com.ssafy.s309.domain.user.dto.SettingsUpdateRequest;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.entity.WardGuardian;
import com.ssafy.s309.domain.user.repository.UserRepository;
import com.ssafy.s309.domain.user.repository.WardGuardianRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final WardGuardianRepository wardGuardianRepository;

  // ── Settings ──────────────────────────────────────────────

  public SettingsResponse getSettings(Long userId) {
    User user = findUserById(userId);
    return SettingsResponse.from(user);
  }

  @Transactional
  public SettingsResponse updateSettings(Long userId, SettingsUpdateRequest request) {
    User user = findUserById(userId);
    user.updateSettings(
        request.name(),
        request.age(),
        request.gender(),
        request.phone(),
        request.height(),
        request.weight(),
        request.diabetesType(),
        request.isMedicated(),
        request.targetLow(),
        request.targetHigh(),
        request.weekStartDay());
    return SettingsResponse.from(user);
  }

  // ── Guardian ──────────────────────────────────────────────

  public List<GuardianResponse> getGuardians(Long wardId) {
    findUserById(wardId);
    return wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(wardId).stream()
        .map(GuardianResponse::from)
        .toList();
  }

  @Transactional
  public GuardianResponse createGuardian(Long wardId, GuardianRequest request) {
    User ward = findUserById(wardId);
    User guardian = findUserById(request.guardianId());
    int nextPriority = wardGuardianRepository.countByWard_Id(wardId);
    WardGuardian wg =
        WardGuardian.builder()
            .ward(ward)
            .guardian(guardian)
            .relation(request.relation())
            .priority(nextPriority)
            .build();
    return GuardianResponse.from(wardGuardianRepository.save(wg));
  }

  @Transactional
  public GuardianResponse updateGuardian(
      Long wardId, Long wardGuardianId, GuardianRequest request) {
    WardGuardian wg = findWardGuardianByIdAndWardId(wardGuardianId, wardId);
    wg.updateRelation(request.relation());
    return GuardianResponse.from(wg);
  }

  @Transactional
  public void deleteGuardian(Long wardId, Long wardGuardianId) {
    WardGuardian wg = findWardGuardianByIdAndWardId(wardGuardianId, wardId);
    int deletedPriority = wg.getPriority();
    wardGuardianRepository.delete(wg);
    wardGuardianRepository.flush();

    List<WardGuardian> remaining =
        wardGuardianRepository.findAllByWard_IdOrderByPriorityAsc(wardId);
    remaining.stream()
        .filter(g -> g.getPriority() > deletedPriority)
        .forEach(g -> g.updatePriority(g.getPriority() - 1));
  }

  // ── 내부 헬퍼 ─────────────────────────────────────────────

  private User findUserById(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다: " + userId));
  }

  private WardGuardian findWardGuardianByIdAndWardId(Long wardGuardianId, Long wardId) {
    WardGuardian wg =
        wardGuardianRepository
            .findById(wardGuardianId)
            .orElseThrow(
                () -> new IllegalArgumentException("존재하지 않는 보호자 관계입니다: " + wardGuardianId));
    if (!Objects.equals(wg.getWard().getId(), wardId)) {
      throw new IllegalArgumentException("해당 유저의 보호자가 아닙니다");
    }
    return wg;
  }
}
