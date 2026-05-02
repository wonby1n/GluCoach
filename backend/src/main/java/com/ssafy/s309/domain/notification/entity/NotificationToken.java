package com.ssafy.s309.domain.notification.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import com.ssafy.s309.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "notification_tokens",
    indexes = @Index(name = "idx_noti_user_active", columnList = "user_id, is_active"))
public class NotificationToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 200)
  private String token;

  @Column(nullable = false, length = 10)
  private String deviceType = "android";

  @Column(nullable = false)
  private Boolean isActive = true;

  @Builder
  public NotificationToken(User user, String token, String deviceType) {
    this.user = user;
    this.token = token;
    if (deviceType != null) this.deviceType = deviceType;
  }

  public void deactivate() {
    this.isActive = false;
  }

  public void updateToken(String token) {
    this.token = token;
    this.isActive = true;
  }
}
