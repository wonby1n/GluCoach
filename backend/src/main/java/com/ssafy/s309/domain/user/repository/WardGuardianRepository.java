package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.WardGuardian;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardGuardianRepository extends JpaRepository<WardGuardian, Integer> {

  List<WardGuardian> findAllByWard_IdOrderByPriorityAsc(Integer wardId);

  int countByWard_Id(Integer wardId);
}
