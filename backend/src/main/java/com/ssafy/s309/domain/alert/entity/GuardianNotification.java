package com.ssafy.s309.domain.alert.entity;

import com.ssafy.s309.domain.user.entity.WardGuardian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "guardian_notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianNotification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  /** 레거시. alerts DROP(V13) 시 컬럼 제거 예정. 신규 INSERT는 chatMessageId 사용. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "alert_id")
  private Alert alert;

  @Column(name = "chat_message_id")
  private Long chatMessageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guard_relation_id", nullable = false)
  private WardGuardian wardGuardian;

  @Column(name = "latitude", precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(name = "longitude", precision = 9, scale = 6)
  private BigDecimal longitude;

  @CreatedDate
  @Column(name = "sent_at", nullable = false, updatable = false)
  private LocalDateTime sentAt;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  @Builder
  private GuardianNotification(
      Long chatMessageId, WardGuardian wardGuardian, BigDecimal latitude, BigDecimal longitude) {
    this.chatMessageId = chatMessageId;
    this.wardGuardian = wardGuardian;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}
