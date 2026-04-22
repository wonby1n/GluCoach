package com.ssafy.s309.domain.user.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guardians", indexes = @Index(name = "idx_guardian_user", columnList = "user_id"))
@SuppressWarnings({"FieldMayBeFinal", "unused"}) // JPA 엔티티 필드는 Hibernate 리플렉션 주입 대상
public class Guardian extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID guardianId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false, length = 20)
  private String phone;

  @Column(length = 32)
  private String relation;

  @Column(nullable = false)
  private Boolean isPrimary = false;

  @Column(nullable = false)
  private Integer priority = 0;

  @Builder
  private Guardian(
      User user, String name, String phone, String relation, Boolean isPrimary, Integer priority) {
    this.user = user;
    this.name = name;
    this.phone = phone;
    this.relation = relation;
    if (isPrimary != null) this.isPrimary = isPrimary;
    if (priority != null) this.priority = priority;
  }

  public void update(String name, String phone, String relation, Boolean isPrimary) {
    if (name != null) this.name = name;
    if (phone != null) this.phone = phone;
    if (relation != null) this.relation = relation;
    if (isPrimary != null) this.isPrimary = isPrimary;
  }

  public void updatePriority(int priority) {
    this.priority = priority;
  }
}
