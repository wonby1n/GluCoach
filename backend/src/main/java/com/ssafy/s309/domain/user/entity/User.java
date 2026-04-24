package com.ssafy.s309.domain.user.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity // "이 클래스는 DB 테이블이야"
@Table(name = "users") // 테이블명 지정
@SuppressWarnings({"FieldMayBeFinal", "unused"}) // JPA 엔티티 필드는 Hibernate 리플렉션 주입 대상
public class User extends BaseEntity {

  @Id // Primary Key
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID userId;

  @Column(nullable = false, unique = true)
  private String email;

  private String password;

  @Column(nullable = false, length = 32)
  private String provider = "email";

  private Float height; // 키 (cm)

  private Float weight; // 체중 (kg)

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private DiabetesType diabetesType = DiabetesType.NONE;

  @Column(nullable = false)
  private Boolean isMedicated = false; // 당뇨약/인슐린 복용 여부

  @Column(nullable = false)
  private Integer targetLow = 70; // 목표 혈당 하한

  @Column(nullable = false)
  private Integer targetHigh = 140; // 목표 혈당 상한

  @Column(nullable = false)
  private Integer alertLow = 70; // 저혈당 알림 기준

  @Column(nullable = false)
  private Integer alertHigh = 180; // 고혈당 알림 기준

  @Column(nullable = false)
  private Boolean nightWatch = false; // 야간 모니터링 여부

  @Column(nullable = false, length = 32)
  private String characterType = "BASIC"; // 캐릭터 타입

  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Guardian> guardians = new ArrayList<>();

  @Builder
  private User(String email, String password, String provider, Float height, Float weight) {
    this.email = email;
    this.password = password;
    if (provider != null) this.provider = provider;
    this.height = height;
    this.weight = weight;
  }

  public boolean isDeleted() {
    return this.deletedAt != null;
  }

  public void withdraw() {
    this.deletedAt = LocalDateTime.now();
    this.email = "deleted_" + this.userId + "@withdrawn.local";
    this.password = null;
    this.height = null;
    this.weight = null;
    this.guardians.clear();
  }

  public void updateSettings(
      Float height,
      Float weight,
      DiabetesType diabetesType,
      Boolean isMedicated,
      Integer targetLow,
      Integer targetHigh,
      Integer alertLow,
      Integer alertHigh,
      Boolean nightWatch,
      String characterType) {
    if (height != null) this.height = height;
    if (weight != null) this.weight = weight;
    if (diabetesType != null) this.diabetesType = diabetesType;
    if (isMedicated != null) this.isMedicated = isMedicated;
    if (targetLow != null) this.targetLow = targetLow;
    if (targetHigh != null) this.targetHigh = targetHigh;
    if (alertLow != null) this.alertLow = alertLow;
    if (alertHigh != null) this.alertHigh = alertHigh;
    if (nightWatch != null) this.nightWatch = nightWatch;
    if (characterType != null) this.characterType = characterType;
  }
}
