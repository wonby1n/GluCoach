package com.ssafy.s309.domain.meal.repository;

import com.ssafy.s309.domain.meal.entity.UserFoodGrade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFoodGradeRepository extends JpaRepository<UserFoodGrade, Integer> {

  Optional<UserFoodGrade> findByUserIdAndFoodId(Integer userId, Integer foodId);
}
