package com.ssafy.s309.domain.user.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;

@Getter
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
}
