package com.ssafy.s309.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ssafy.s309.domain.food.client.FoodApiClient;
import com.ssafy.s309.domain.food.client.dto.FoodApiItem;
import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FoodResolutionService 의 오케스트레이션 검증. cache hit / API 호출 / helper 위임 흐름만 본다. 실제 매칭·저장 로직은
 * FoodResolutionTxHelper 의 책임이라 거기서 별도 테스트.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodResolutionServiceTest {

  @Mock private FoodApiClient foodApiClient;
  @Mock private FoodResolutionTxHelper tx;
  @InjectMocks private FoodResolutionService resolutionService;

  private static Food food(Integer id, String name) {
    Food f =
        Food.builder()
            .name(name)
            .carbsG(new BigDecimal("31.50"))
            .searchCount(1)
            .cachedAt(java.time.LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  @Test
  void cache_hit_시_API_호출_없이_CACHE_HIT_반환() {
    Food cached = food(10, "비빔밥");
    given(tx.findAndMarkCacheHit("비빔밥")).willReturn(Optional.of(cached));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.CACHE_HIT);
    assertThat(result.food()).isSameAs(cached);
    verifyNoInteractions(foodApiClient);
    verify(tx, never()).resolveRemoteOrPending(anyList(), anyString());
  }

  @Test
  void cache_miss_시_API_호출_후_helper_위임() {
    FoodApiItem apiItem =
        new FoodApiItem(
            "D000007", "비빔밥_육회", null, null, "150", "5.0", "1.0", "32.0", "0.2", null, null, null,
            null, null);
    Food upserted = food(20, "비빔밥_육회");
    given(tx.findAndMarkCacheHit("비빔밥")).willReturn(Optional.empty());
    given(foodApiClient.search("비빔밥")).willReturn(List.of(apiItem));
    given(tx.resolveRemoteOrPending(List.of(apiItem), "비빔밥"))
        .willReturn(new FoodResolution(upserted, ResolutionStatus.REMOTE_FETCHED));

    FoodResolution result = resolutionService.resolve("비빔밥");

    assertThat(result.status()).isEqualTo(ResolutionStatus.REMOTE_FETCHED);
    assertThat(result.food()).isSameAs(upserted);
  }

  @Test
  void API_예외_시_빈_리스트로_helper_위임_PENDING_NUTRITION() {
    Food customized = food(30, "미지의음식");
    given(tx.findAndMarkCacheHit(anyString())).willReturn(Optional.empty());
    given(foodApiClient.search(anyString())).willThrow(new FoodApiException("식약처 API 연결 실패"));
    given(tx.resolveRemoteOrPending(eq(List.of()), eq("미지의음식")))
        .willReturn(new FoodResolution(customized, ResolutionStatus.PENDING_NUTRITION));

    FoodResolution result = resolutionService.resolve("미지의음식");

    assertThat(result.status()).isEqualTo(ResolutionStatus.PENDING_NUTRITION);
    verify(tx).resolveRemoteOrPending(eq(List.of()), eq("미지의음식"));
  }

  @Test
  void 빈_음식명_입력_시_IllegalArgumentException() {
    assertThatThrownBy(() -> resolutionService.resolve("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolutionService.resolve(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> resolutionService.resolve(null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(foodApiClient, tx);
  }

  @Test
  void 음식명_공백_trim_정규화() {
    Food cached = food(40, "비빔밥");
    given(tx.findAndMarkCacheHit("비빔밥")).willReturn(Optional.of(cached));

    resolutionService.resolve("  비빔밥  ");

    verify(tx).findAndMarkCacheHit(eq("비빔밥"));
  }
}
