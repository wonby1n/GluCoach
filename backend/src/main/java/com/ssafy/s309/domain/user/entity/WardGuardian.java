package com.ssafy.s309.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "ward_guardian",
    indexes = {
      @Index(name = "idx_wg_ward", columnList = "ward_id"),
      @Index(name = "idx_wg_guardian", columnList = "guardian_id")
    })
public class WardGuardian {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ward_id", nullable = false)
  private User ward;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guardian_id", nullable = false)
  private User guardian;

  @Column(length = 10)
  private String relation;

  @Column(nullable = false, columnDefinition = "TINYINT")
  private Integer priority = 0;

  @Builder
  private WardGuardian(User ward, User guardian, String relation, Integer priority) {
    this.ward = ward;
    this.guardian = guardian;
    this.relation = relation;
    if (priority != null) this.priority = priority;
  }

  public void updateRelation(String relation) {
    if (relation != null) this.relation = relation;
  }

  public void updatePriority(int priority) {
    this.priority = priority;
  }
}
