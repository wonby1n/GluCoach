package com.ssafy.s309.domain.medication.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "medications_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationRecord extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "user_id", nullable = false)
  private Integer userId;

  @Column(name = "memo", length = 200)
  private String memo;

  @Column(name = "taken_at", nullable = false)
  private LocalDateTime takenAt;
}
