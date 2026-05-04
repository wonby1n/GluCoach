package com.ssafy.s309.domain.agent.repository;

import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentPendingTriggerRepository extends JpaRepository<AgentPendingTrigger, Integer> {

  @Query(
      """
      SELECT t FROM AgentPendingTrigger t
      WHERE t.isDispatched = false
        AND t.scheduledAt <= :now
      """)
  List<AgentPendingTrigger> findPendingTriggers(@Param("now") LocalDateTime now);
}
