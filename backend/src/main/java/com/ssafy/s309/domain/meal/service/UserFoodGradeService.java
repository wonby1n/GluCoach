package com.ssafy.s309.domain.meal.service;

import com.ssafy.s309.domain.meal.dto.FoodGradeResponse;
import com.ssafy.s309.domain.meal.repository.UserFoodGradeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFoodGradeService {

  private final UserFoodGradeRepository userFoodGradeRepository;

  public List<FoodGradeResponse> getMyFoodGrades(Integer userId) {
    return userFoodGradeRepository.findGradesByUserId(userId);
  }
}
