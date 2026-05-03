package com.ssafy.s309.domain.user.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
@SuppressWarnings({"FieldMayBeFinal", "unused"})
public class User extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, unique = true)
  private String email;

  private String password;

  @Column(nullable = false, length = 10)
  private String provider = "email";

  @Column(length = 20)
  private String name;

  private Short age;

  @Column(length = 6)
  private String gender;

  @Column(length = 20)
  private String phone;

  @Column(precision = 4, scale = 1)
  private BigDecimal height;

  @Column(precision = 4, scale = 1)
  private BigDecimal weight;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private DiabetesType diabetesType;

  private Boolean isMedicated;

  @Column(precision = 5, scale = 2)
  private BigDecimal targetLow;

  @Column(precision = 5, scale = 2)
  private BigDecimal targetHigh;

  @Column(nullable = false)
  private Short weekStartDay = (short) 1;

  private LocalDateTime deletedAt;

  @OneToMany(mappedBy = "ward", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<WardGuardian> wardGuardians = new ArrayList<>();

  @Builder
  private User(
      String email,
      String password,
      String provider,
      String name,
      String phone,
      BigDecimal height,
      BigDecimal weight) {
    this.email = email;
    this.password = password;
    if (provider != null) this.provider = provider;
    this.name = name;
    this.phone = phone;
    this.height = height;
    this.weight = weight;
  }

  public boolean isDeleted() {
    return this.deletedAt != null;
  }

  public LocalDateTime getDeletedAt() {
    return this.deletedAt;
  }

  public void withdraw() {
    this.deletedAt = LocalDateTime.now();
    this.email = "deleted_" + this.id + "@withdrawn.local";
    this.password = null;
    this.height = null;
    this.weight = null;
    this.wardGuardians.clear();
  }

  public void updateSettings(
      String name,
      Short age,
      String gender,
      String phone,
      BigDecimal height,
      BigDecimal weight,
      DiabetesType diabetesType,
      Boolean isMedicated,
      BigDecimal targetLow,
      BigDecimal targetHigh,
      Short weekStartDay) {
    if (name != null) this.name = name;
    if (age != null) this.age = age;
    if (gender != null) this.gender = gender;
    if (phone != null) this.phone = phone;
    if (height != null) this.height = height;
    if (weight != null) this.weight = weight;
    if (diabetesType != null) this.diabetesType = diabetesType;
    if (isMedicated != null) this.isMedicated = isMedicated;
    if (targetLow != null) this.targetLow = targetLow;
    if (targetHigh != null) this.targetHigh = targetHigh;
    if (weekStartDay != null) this.weekStartDay = weekStartDay;
  }
}
