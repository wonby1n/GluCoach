package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodResolutionServiceTest {

  @Mock private FoodRepository foodRepository;
  @Mock private FoodApiClient foodApiClient;
  @Mock private FoodService foodService;
  @InjectMocks private FoodResolutionService resolutionService;

  private static Food foodWithNutrition(Integer id, String name, int searchCount) {
    Food f =
        Food.builder()
            .name(name)
            .kcal(new BigDecimal("143.00"))
            .carbsG(new BigDecimal("31.50"))
            .proteinG(new BigDecimal("2.60"))
            .fatG(new BigDecimal("0.30"))
            .searchCount(searchCount)
            .cachedAt(LocalDateTime.now().minusDays(60))
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  private static Food foodWithoutNutrition(Integer id, String name) {
    Food f =
        Food.builder()
            .name(name)
            .searchCount(0)
            .cachedAt(LocalDateTime.now())
            .isCustomized(true)
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void DB_캐시에_영양정보있는_row_있으면_CACHE_HIT() {
    Food cached = foodWithNutrition(10, "비빔밥", 5);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥"))
        .willReturn(List.of(cached));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.CACHE_HIT);
    assertThat(result.food()).isSameAs(cached);
    assertThat(cached.getSearchCount()).isEqualTo(6);
    verifyNoInteractions(foodApiClient, foodService);
  }

  @Test
  void 정확매칭이_검색량높은_부분매칭보다_우선() {
    // CV "비빔밥" 입력 시 "돼지비빔밥(sc=500)"이 검색량 정렬로 "비빔밥(sc=10)"보다 앞서는 오매칭 방지.
    Food exact = foodWithNutrition(100, "비빔밥", 10);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥"))
        .willReturn(List.of(exact));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.CACHE_HIT);
    assertThat(result.food()).isSameAs(exact);
    // 정확 매칭 히트 시 부분일치 쿼리는 호출되지 않아야 함.
    verify(foodRepository, never())
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            anyString(), any(LocalDateTime.class));
  }

  @Test
  void 정확매칭_없으면_부분매칭으로_폴백() {
    Food partial = foodWithNutrition(101, "비빔밥_돼지머리", 50);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥")).willReturn(List.of());
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    eq("비빔밥"), any(LocalDateTime.class)))
        .willReturn(List.of(partial));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.CACHE_HIT);
    assertThat(result.food()).isSameAs(partial);
  }

  @Test
  void 정확매칭_있어도_carbsG_NULL이면_부분매칭으로_폴백() {
    Food exactNoCarbs = foodWithoutNutrition(102, "비빔밥");
    Food partial = foodWithNutrition(103, "비빔밥_육회", 1);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥"))
        .willReturn(List.of(exactNoCarbs));
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    eq("비빔밥"), any(LocalDateTime.class)))
        .willReturn(List.of(partial));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.CACHE_HIT);
    assertThat(result.food()).isSameAs(partial);
  }

  @Test
  void DB_row있어도_carbsG_NULL이면_무시하고_API_호출() {
    Food noNutrition = foodWithoutNutrition(11, "비빔밥");
    Food fromApi = foodWithNutrition(12, "비빔밥_육회", 0);
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of(noNutrition));
    given(foodApiClient.search("비빔밥")).willReturn(List.of());
    given(foodService.upsertFromApi(anyList())).willReturn(List.of(fromApi));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.REMOTE_FETCHED);
    assertThat(result.food()).isSameAs(fromApi);
    assertThat(fromApi.getSearchCount()).isEqualTo(1);
  }

  @Test
  void DB_미스_API_매치_있으면_REMOTE_FETCHED() {
    Food upserted = foodWithNutrition(20, "비빔밥_돼지머리", 0);
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search("비빔밥")).willReturn(List.of());
    given(foodService.upsertFromApi(anyList())).willReturn(List.of(upserted));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.REMOTE_FETCHED);
    assertThat(result.food()).isSameAs(upserted);
    assertThat(upserted.getSearchCount()).isEqualTo(1);
    verify(foodRepository, never()).save(any(Food.class));
  }

  @Test
  void DB_미스_API_빈리스트면_PENDING_NUTRITION_customized_저장() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search("미지의음식")).willReturn(List.of());
    given(foodService.upsertFromApi(anyList())).willReturn(List.of());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    FoodResolution result = resolutionService.resolve("미지의음식");

    assertThat(result.status()).isEqualTo(ResolutionStatus.PENDING_NUTRITION);
    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    Food saved = captor.getValue();
    assertThat(saved.getName()).isEqualTo("미지의음식");
    assertThat(saved.isCustomized()).isTrue();
    assertThat(saved.getCarbsG()).isNull();
    assertThat(saved.getSearchCount()).isEqualTo(1);
    assertThat(saved.getCachedAt())
        .asInstanceOf(InstanceOfAssertFactories.LOCAL_DATE_TIME)
        .isAfter(LocalDateTime.now().minusMinutes(1));
  }

  @Test
  void API_예외_발생하면_PENDING_NUTRITION으로_폴백() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search("비빔밥")).willThrow(new FoodApiException("식약처 API 연결 실패"));
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.PENDING_NUTRITION);
    assertThat(result.food().isCustomized()).isTrue();
    verify(foodService, never()).upsertFromApi(anyList());
  }

  @Test
  void API_결과있어도_carbs없어서_upsert_빈리스트면_PENDING_NUTRITION() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());
    given(foodApiClient.search("비빔밥")).willReturn(List.of());
    given(foodService.upsertFromApi(anyList())).willReturn(List.of());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.PENDING_NUTRITION);
  }

  @Test
  void 빈_음식명_입력_시_IllegalArgumentException() {
    assertThatThrownBy(() -> resolutionService.resolve("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolutionService.resolve(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolutionService.resolve(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 음식명_공백_trim_정규화() {
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    eq("비빔밥"), any(LocalDateTime.class)))
        .willReturn(List.of(foodWithNutrition(30, "비빔밥", 1)));

    resolutionService.resolve("  비빔밥  ");

    verify(foodRepository)
        .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
            eq("비빔밥"), any(LocalDateTime.class));
  }
}
