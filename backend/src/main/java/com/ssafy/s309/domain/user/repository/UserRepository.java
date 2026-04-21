package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
