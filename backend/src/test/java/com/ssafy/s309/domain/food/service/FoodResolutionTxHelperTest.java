package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
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

/** 짧은 tx 단위로 분리된 매칭·저장 로직 검증. (이전 FoodResolutionServiceTest 의 매칭 케이스를 본 helper 로 이전.) */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodResolutionTxHelperTest {

  @Mock private FoodRepository foodRepository;
  @Mock private FoodApiUpsertHelper upsertHelper;
  @InjectMocks private FoodResolutionTxHelper tx;

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

  // ───────── findAndMarkCacheHit ─────────

  @Test
  void DB_캐시에_영양정보있는_row_있으면_CACHE_HIT() {
    Food cached = foodWithNutrition(10, "비빔밥", 5);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥"))
        .willReturn(List.of(cached));

    Optional<Food> result = tx.findAndMarkCacheHit("비빔밥");

    assertThat(result).contains(cached);
    assertThat(cached.getSearchCount()).isEqualTo(6);
  }

  @Test
  void 정확매칭이_검색량높은_부분매칭보다_우선() {
    // CV "비빔밥" 입력 시 "돼지비빔밥(sc=500)"이 검색량 정렬로 "비빔밥(sc=10)"보다 앞서는 오매칭 방지.
    Food exact = foodWithNutrition(100, "비빔밥", 10);
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc("비빔밥"))
        .willReturn(List.of(exact));

    Optional<Food> result = tx.findAndMarkCacheHit("비빔밥");

    assertThat(result).contains(exact);
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

    Optional<Food> result = tx.findAndMarkCacheHit("비빔밥");

    assertThat(result).contains(partial);
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

    Optional<Food> result = tx.findAndMarkCacheHit("비빔밥");

    assertThat(result).contains(partial);
  }

  @Test
  void 캐시_미스면_빈_Optional() {
    given(foodRepository.findByNameIgnoreCaseOrderBySearchCountDesc(anyString()))
        .willReturn(List.of());
    given(
            foodRepository
                .findTop20ByNameContainingIgnoreCaseAndCachedAtAfterOrderBySearchCountDesc(
                    anyString(), any(LocalDateTime.class)))
        .willReturn(List.of());

    Optional<Food> result = tx.findAndMarkCacheHit("미지의음식");

    assertThat(result).isEmpty();
  }

  // ───────── resolveRemoteOrPending ─────────

  @Test
  void API_매치_있으면_REMOTE_FETCHED() {
    Food upserted = foodWithNutrition(20, "비빔밥_돼지머리", 0);
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of(upserted));

    FoodResolution result = tx.resolveRemoteOrPending(List.of(), "비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.REMOTE_FETCHED);
    assertThat(result.food()).isSameAs(upserted);
    assertThat(upserted.getSearchCount()).isEqualTo(1);
    verify(foodRepository, never()).save(any(Food.class));
  }

  @Test
  void API_정확매칭이_substring보다_우선() {
    Food substring = foodWithNutrition(21, "전주비빔밥", 100);
    Food exact = foodWithNutrition(22, "비빔밥", 1);
    Food unrelated = foodWithNutrition(23, "국밥_돼지머리", 500);
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of(unrelated, substring, exact));

    FoodResolution result = tx.resolveRemoteOrPending(List.of(), "비빔밥");

    assertThat(result.food()).isSameAs(exact);
  }

  @Test
  void API_정확매칭없으면_substring_매칭_사용() {
    Food substring = foodWithNutrition(24, "전주비빔밥", 0);
    Food unrelated = foodWithNutrition(25, "국밥_돼지머리", 999);
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of(unrelated, substring));

    FoodResolution result = tx.resolveRemoteOrPending(List.of(), "비빔밥");

    assertThat(result.food()).isSameAs(substring);
  }

  @Test
  void API_무관한_음식들뿐이면_PENDING_NUTRITION_폴백() {
    Food unrelated1 = foodWithNutrition(26, "국밥_돼지머리", 500);
    Food unrelated2 = foodWithNutrition(27, "곰탕", 300);
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of(unrelated1, unrelated2));
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    FoodResolution result = tx.resolveRemoteOrPending(List.of(), "비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.PENDING_NUTRITION);
    ArgumentCaptor<Food> captor = ArgumentCaptor.forClass(Food.class);
    verify(foodRepository).save(captor.capture());
    assertThat(captor.getValue().isCustomized()).isTrue();
    assertThat(captor.getValue().getName()).isEqualTo("비빔밥");
  }

  @Test
  void API_빈리스트면_PENDING_NUTRITION_customized_저장() {
    given(upsertHelper.upsertFromApi(anyList())).willReturn(List.of());
    given(foodRepository.save(any(Food.class))).willAnswer(inv -> inv.getArgument(0));

    FoodResolution result = tx.resolveRemoteOrPending(List.of(), "미지의음식");

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
}
