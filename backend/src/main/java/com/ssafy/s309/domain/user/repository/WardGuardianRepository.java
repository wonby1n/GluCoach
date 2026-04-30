package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.WardGuardian;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardGuardianRepository extends JpaRepository<WardGuardian, Long> {

  List<WardGuardian> findAllByWard_IdOrderByPriorityAsc(Long wardId);

  int countByWard_Id(Long wardId);
}
