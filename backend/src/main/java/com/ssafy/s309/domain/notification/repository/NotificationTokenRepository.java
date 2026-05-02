package com.ssafy.s309.domain.notification.repository;

import com.ssafy.s309.domain.notification.entity.DeviceType;
import com.ssafy.s309.domain.notification.entity.NotificationToken;
import com.ssafy.s309.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Integer> {

  List<NotificationToken> findByUserAndIsActiveTrue(User user);

  Optional<NotificationToken> findByUserAndDeviceType(User user, DeviceType deviceType);
}
