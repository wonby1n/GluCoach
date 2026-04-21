package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.Guardian;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@SuppressWarnings("unused") // Service 계층 구현 전까지 미사용
public interface GuardianRepository extends JpaRepository<Guardian, UUID> {

  List<Guardian> findAllByUser_UserId(UUID userId);
}
