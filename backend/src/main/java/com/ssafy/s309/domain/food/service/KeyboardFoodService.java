package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.dto.KeyboardFoodItem;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeyboardFoodService {

  private final FoodRepository foodRepository;

  @Transactional(readOnly = true)
  public List<KeyboardFoodItem> getKeyboardFoods(Integer userId) {
    return foodRepository.findAllKeyboardFoods(userId);
  }
}
