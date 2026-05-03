package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentUserProfileResponse;
import com.ssafy.s309.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentUserProfileService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public AgentUserProfileResponse getUserProfile(Integer userId) {
    return userRepository
        .findById(userId)
        .filter(u -> !u.isDeleted())
        .map(AgentUserProfileResponse::from)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + userId));
  }
}
