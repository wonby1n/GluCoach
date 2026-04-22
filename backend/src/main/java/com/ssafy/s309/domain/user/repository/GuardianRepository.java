package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.Guardian;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

  List<Guardian> findAllByUser_UserIdOrderByPriorityAsc(UUID userId);

  int countByUser_UserId(UUID userId);
}
