package com.ssafy.s309.domain.meal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * MealRecordResponse.from() 의 필드 매핑 검증.
 *
 * <p>주 목적은 foodDisplayName 이 누락 없이 전달되는지, food 가 null 일 때 양쪽 다 null 인지 보장 — record signature 가 또 바뀔
 * 때 무음 통과를 막기 위함.
 */
@SuppressWarnings("NonAsciiCharacters")
class MealRecordResponseTest {

  @Test
  void from_은_food_의_name_과_displayName_을_모두_전달한다() {
    Food food =
        Food.builder()
            .id(10)
            .name("김밥_소고기")
            .displayName("소고기김밥")
            .isCustomized(false)
            .searchCount(0)
            .cachedAt(LocalDateTime.now())
            .build();
    MealRecord meal =
        MealRecord.builder()
            .id(1)
            .userId(100)
            .foodId(10)
            .food(food)
            .recordedAt(LocalDateTime.now())
            .build();

    MealRecordResponse result = MealRecordResponse.from(meal, "https://img", null);

    assertThat(result.foodName()).isEqualTo("김밥_소고기");
    assertThat(result.foodDisplayName()).isEqualTo("소고기김밥");
  }

  @Test
  void from_은_food_가_null_이면_양쪽_다_null_을_반환한다() {
    MealRecord meal =
        MealRecord.builder()
            .id(2)
            .userId(100)
            .foodId(null)
            .food(null)
            .recordedAt(LocalDateTime.now())
            .build();

    MealRecordResponse result = MealRecordResponse.from(meal, null, null);

    assertThat(result.foodName()).isNull();
    assertThat(result.foodDisplayName()).isNull();
  }
}
