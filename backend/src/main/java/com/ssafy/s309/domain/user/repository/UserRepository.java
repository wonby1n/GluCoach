package com.ssafy.s309.domain.user.repository;

import com.ssafy.s309.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

  Optional<User> findByEmail(String email);

  Optional<User> findByEmailAndDeletedAtIsNull(String email);

  Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

  boolean existsByEmail(String email);
}
