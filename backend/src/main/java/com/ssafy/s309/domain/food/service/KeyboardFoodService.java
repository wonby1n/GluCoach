package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.dto.KeyboardFoodItem;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeyboardFoodService {

  private final FoodRepository foodRepository;

  @Transactional(readOnly = true)
  public List<KeyboardFoodItem> getKeyboardFoods(Integer userId) {
    List<KeyboardFoodItem> graded = foodRepository.findKeyboardGradesByUserId(userId);
    Set<String> gradedNames =
        graded.stream().map(KeyboardFoodItem::name).collect(Collectors.toSet());

    List<KeyboardFoodItem> popular =
        foodRepository.findTop200ByOrderBySearchCountDesc().stream()
            .filter(f -> !gradedNames.contains(f.getName()))
            .map(f -> new KeyboardFoodItem(f.getName(), f.getCategory(), null))
            .toList();

    List<KeyboardFoodItem> result = new ArrayList<>(graded);
    result.addAll(popular);
    return result;
  }
}
