package com.ssafy.s309.domain.notification.entity;

import com.ssafy.s309.common.entity.BaseEntity;
import com.ssafy.s309.domain.user.entity.User;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_tokens")
public class NotificationToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(columnDefinition = "uuid")
  private UUID tokenId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 16)
  private String deviceType = "android";

  @Column(nullable = false, length = 512)
  private String fcmToken;

  @Column(nullable = false)
  private Boolean isActive = true;

  @Builder
  public NotificationToken(User user, String fcmToken, String deviceType) {
    this.user = user;
    this.fcmToken = fcmToken;
    if (deviceType != null) this.deviceType = deviceType;
  }

  public void deactivate() {
    this.isActive = false;
  }

  public void updateToken(String fcmToken) {
    this.fcmToken = fcmToken;
    this.isActive = true;
  }
}
