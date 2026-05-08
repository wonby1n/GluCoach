package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 식약처 API 결과 upsert / 영양정보 결측 차단 / serving_size 파싱 등 행동 검증 (이전 FoodServiceTest 케이스 이전). */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodApiUpsertHelperTest {

  @Mock private FoodRepository foodRepository;
  @InjectMocks private FoodApiUpsertHelper upsertHelper;

  private static Food existingFood(Integer id, String apiId, int searchCount) {
    Food f =
        Food.builder()
            .foodApiId(apiId)
            .name("현미밥")
            .kcal(new BigDecimal("143.00"))
            .carbsG(new BigDecimal("31.50"))
            .searchCount(searchCount)
            .cachedAt(LocalDateTime.now().minusDays(60))
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void 신규_API_item_은_INSERT() {
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "현미밥", null, null, "150", "5.0", "1.0", "32.0", "0.2", null, null, null,
            null, null);
    given(foodRepository.findByFoodApiId("D000007")).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    upsertHelper.upsertFromApi(List.of(apiItem));

    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    Food saved = captor.getValue();
    assertThat(saved.getFoodApiId()).isEqualTo("D000007");
    assertThat(saved.getName()).isEqualTo("현미밥");
    assertThat(saved.getKcal()).isEqualByComparingTo("150");
    assertThat(saved.getCarbsG()).isEqualByComparingTo("32.0");
    assertThat(saved.isCustomized()).isFalse();
  }

  @Test
  void 기존_entity_있으면_refresh_save_없음() {
    Food existing = existingFood(2, "D000007", 10);
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "현미밥", null, null, "155", "5.5", "1.1", "33.0", "0.3", null, null, null,
            null, null);
    given(foodRepository.findByFoodApiId("D000007")).willReturn(Optional.of(existing));

    upsertHelper.upsertFromApi(List.of(apiItem));

    verify(foodRepository, never()).save(any(Food.class));
    assertThat(existing.getKcal()).isEqualByComparingTo("155");
    assertThat(existing.getCarbsG()).isEqualByComparingTo("33.0");
    assertThat(existing.getCachedAt())
        .asInstanceOf(InstanceOfAssertFactories.LOCAL_DATE_TIME)
        .isAfter(LocalDateTime.now().minusMinutes(1));
  }

  @Test
  void 탄수화물_결측_item_은_차단() {
    // carbsG=null → upsert 안 됨. 혈당 예측 모델 핵심 입력이라 결측 시 비교 시뮬레이션 무의미.
    FoodApiItem invalid =
        new FoodApiItem(
            "D000008", "차단대상", null, null, "100", null, null, null, null, null, null, null, null,
            null);
    FoodApiItem valid =
        new FoodApiItem(
            "D000009", "통과대상", null, null, "100", null, null, "20.0", null, null, null, null, null,
            null);
    given(foodRepository.findByFoodApiId("D000009")).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    List<Food> result = upsertHelper.upsertFromApi(List.of(invalid, valid));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo("통과대상");
    verify(foodRepository, never()).findByFoodApiId("D000008");
  }

  @Test
  void serving_size_숫자만_추출() {
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000010", "테스트", null, "100g", "100", null, null, "20.0", null, null, null, null, null,
            null);
    given(foodRepository.findByFoodApiId("D000010")).willReturn(Optional.empty());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    upsertHelper.upsertFromApi(List.of(apiItem));

    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    assertThat(captor.getValue().getServingSize()).isEqualByComparingTo("100");
  }
}
