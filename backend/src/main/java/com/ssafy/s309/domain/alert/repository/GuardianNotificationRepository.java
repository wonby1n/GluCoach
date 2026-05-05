package com.ssafy.s309.domain.alert.repository;

import com.ssafy.s309.domain.alert.entity.GuardianNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianNotificationRepository
    extends JpaRepository<GuardianNotification, Integer> {}
