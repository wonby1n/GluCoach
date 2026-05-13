package com.ssafy.s309.domain.notification.repository;

import com.ssafy.s309.domain.notification.entity.DeviceType;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Integer> {

  List<NotificationToken> findByUserAndIsActiveTrue(User user);

  /** User 엔티티 lookup 없이 user_id로 active 토큰만 조회 (ChatFcmDispatcher 발송 wire-up용). */
  List<NotificationToken> findByUser_IdAndIsActiveTrue(Integer userId);

  Optional<NotificationToken> findByUserAndDeviceType(User user, DeviceType deviceType);

  /** 로그아웃 시 이 기기의 토큰만 deactivate 하기 위한 lookup. */
  Optional<NotificationToken> findByUserAndToken(User user, String token);
}
