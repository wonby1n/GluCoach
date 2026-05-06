package com.ssafy.s309.domain.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "agent_pending_triggers")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentPendingTrigger {

  public static final String TYPE_POST_MEAL = "post_meal";
  public static final String TYPE_WAKE_UP = "wake_up";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "trigger_type", nullable = false, length = 50)
  private String triggerType;

  @Column(name = "reference_id")
  private Integer referenceId;

  @Column(name = "scheduled_at", nullable = false)
  private LocalDateTime scheduledAt;

  @Column(name = "is_dispatched", nullable = false)
  @Builder.Default
  private Boolean isDispatched = false;

  /**
   * Agent #8 schedule_followup 호출 시 Agent가 보낸 LLM 판단 메모. 폴러 재호출 payload에 echo. 식사 자동 예약(post_meal)
   * 경로에서는 NULL.
   */
  @Column(name = "reason", length = 500)
  private String reason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public void markDispatched() {
    this.isDispatched = true;
  }
}
