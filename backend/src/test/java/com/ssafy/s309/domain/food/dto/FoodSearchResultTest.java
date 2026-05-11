package com.ssafy.s309.domain.food.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.s309.domain.food.entity.Food;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * FoodSearchResult.from() 의 필드 매핑 검증.
 *
 * <p>주 목적은 displayName 이 누락 없이 전달되는지 보장 — record signature 가 또 바뀔 때 무음 통과를 막기 위함.
 */
@SuppressWarnings("NonAsciiCharacters")
class FoodSearchResultTest {

  @Test
  void from_은_엔티티의_displayName_을_그대로_전달한다() {
    Food food =
        Food.builder()
            .id(1)
            .name("김밥_샐러리")
            .displayName("샐러리김밥")
            .isCustomized(false)
            .searchCount(0)
            .cachedAt(LocalDateTime.now())
            .build();

    FoodSearchResult result = FoodSearchResult.from(food);

    assertThat(result.name()).isEqualTo("김밥_샐러리");
    assertThat(result.displayName()).isEqualTo("샐러리김밥");
  }

  @Test
  void from_은_displayName_이_없으면_null_을_전달한다() {
    Food food =
        Food.builder()
            .id(2)
            .name("쌀밥")
            .isCustomized(false)
            .searchCount(0)
            .cachedAt(LocalDateTime.now())
            .build();

    FoodSearchResult result = FoodSearchResult.from(food);

    assertThat(result.name()).isEqualTo("쌀밥");
    assertThat(result.displayName()).isNull();
  }
}
