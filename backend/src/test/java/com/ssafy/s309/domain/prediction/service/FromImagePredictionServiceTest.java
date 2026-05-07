package com.ssafy.s309.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.dto.FoodResolution;
import com.ssafy.s309.domain.food.dto.FoodResolution.ResolutionStatus;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.service.FoodResolutionService;
import com.ssafy.s309.domain.prediction.client.FoodDetectClient;
import com.ssafy.s309.domain.prediction.client.dto.FoodBBox;
import com.ssafy.s309.domain.prediction.client.dto.FoodDetectResponse;
import com.ssafy.s309.domain.prediction.client.dto.FoodDetection;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse.Status;
import com.ssafy.s309.domain.prediction.dto.PredictResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class FromImagePredictionServiceTest {

  @Mock private FoodDetectClient foodDetectClient;
  @Mock private FoodResolutionService foodResolutionService;
  @Mock private PredictionService predictionService;
  @InjectMocks private FromImagePredictionService service;

  private static final Integer USER_ID = 42;

  private static final MultipartFile IMAGE =
      new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});

  private static FoodDetection detection(String nameKo, double confidence) {
    return new FoodDetection(nameKo, "en", confidence, new FoodBBox(0, 0, 10, 10));
  }

  private static Food foodWithCarbs(int id, String name) {
    Food f =
        Food.builder()
            .name(name)
            .carbsG(new BigDecimal("32.0"))
            .searchCount(1)
            .cachedAt(java.time.LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  private static PredictResponse predictResp() {
    return new PredictResponse(999, List.of(), 168.0, 45, 0.85);
  }

  @Test
  void 고신뢰_CACHE_HIT_시_OK_반환() {
    FoodDetection top = detection("비빔밥", 0.91);
    Food food = foodWithCarbs(10, "비빔밥");
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(1, List.of(top)));
    given(foodResolutionService.resolve("비빔밥"))
        .willReturn(new FoodResolution(food, ResolutionStatus.CACHE_HIT));
    given(predictionService.predictForFood(USER_ID, food)).willReturn(predictResp());

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.OK);
    assertThat(result.foodId()).isEqualTo(10);
    assertThat(result.foodName()).isEqualTo("비빔밥");
    assertThat(result.prediction()).isNotNull();
    assertThat(result.requireConfirmation()).isFalse();
    assertThat(result.detected()).hasSize(1);
  }

  @Test
  void REMOTE_FETCHED_시_OK_반환() {
    FoodDetection top = detection("김치", 0.78);
    Food food = foodWithCarbs(20, "김치_배추");
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(1, List.of(top)));
    given(foodResolutionService.resolve("김치"))
        .willReturn(new FoodResolution(food, ResolutionStatus.REMOTE_FETCHED));
    given(predictionService.predictForFood(USER_ID, food)).willReturn(predictResp());

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.OK);
    assertThat(result.foodId()).isEqualTo(20);
    assertThat(result.prediction()).isNotNull();
  }

  @Test
  void 신뢰도_미달_시_LOW_CONFIDENCE_반환_resolve_predict_호출_없음() {
    FoodDetection top = detection("비빔밥", 0.45);
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(1, List.of(top)));

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.LOW_CONFIDENCE);
    assertThat(result.detected()).hasSize(1);
    assertThat(result.foodId()).isNull();
    assertThat(result.foodName()).isNull();
    assertThat(result.prediction()).isNull();
    assertThat(result.requireConfirmation()).isTrue();
    verify(foodResolutionService, never()).resolve(anyString());
    verify(predictionService, never()).predictForFood(anyInt(), any());
  }

  @Test
  void 탐지결과_빈리스트면_LOW_CONFIDENCE_반환() {
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(0, List.of()));

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.LOW_CONFIDENCE);
    assertThat(result.detected()).isEmpty();
    assertThat(result.requireConfirmation()).isTrue();
    verify(foodResolutionService, never()).resolve(anyString());
  }

  @Test
  void PENDING_NUTRITION_시_predict_호출없이_빈_prediction_반환() {
    FoodDetection top = detection("미지의음식", 0.85);
    Food customized = foodWithCarbs(30, "미지의음식"); // carbs 채워넣었지만 status가 PENDING이니 무시
    ReflectionTestUtils.setField(customized, "carbsG", null);
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(1, List.of(top)));
    given(foodResolutionService.resolve("미지의음식"))
        .willReturn(new FoodResolution(customized, ResolutionStatus.PENDING_NUTRITION));

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.PENDING_NUTRITION);
    assertThat(result.foodId()).isEqualTo(30);
    assertThat(result.foodName()).isEqualTo("미지의음식");
    assertThat(result.prediction()).isNull();
    assertThat(result.requireConfirmation()).isFalse();
    verify(predictionService, never()).predictForFood(anyInt(), any());
  }

  @Test
  void 임계값_경계_정확히_0_6은_OK_경로_진입() {
    FoodDetection top = detection("비빔밥", FromImagePredictionService.CONFIDENCE_THRESHOLD);
    Food food = foodWithCarbs(40, "비빔밥");
    given(foodDetectClient.detect(IMAGE)).willReturn(new FoodDetectResponse(1, List.of(top)));
    given(foodResolutionService.resolve(anyString()))
        .willReturn(new FoodResolution(food, ResolutionStatus.CACHE_HIT));
    given(predictionService.predictForFood(eq(USER_ID), eq(food))).willReturn(predictResp());

    FromImagePredictResponse result = service.predict(USER_ID, IMAGE);

    assertThat(result.status()).isEqualTo(Status.OK);
  }
}
