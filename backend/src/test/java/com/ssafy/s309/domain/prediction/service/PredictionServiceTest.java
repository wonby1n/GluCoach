package com.ssafy.s309.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.dto.AbPredictRequest;
import com.ssafy.s309.domain.prediction.dto.AbPredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PredictionServiceTest {

  @Mock private GlucosePredictClient glucosePredictClient;
  @Mock private PredictionTxHelper tx;
  @Mock private FoodRepository foodRepository;
  @Mock private Executor asyncExecutor;
  @InjectMocks private PredictionService predictionService;

  private static final Integer USER_ID = 7;

  private static User user() {
    User u =
        User.builder()
            .email("test@example.com")
            .password("pw")
            .name("테스트")
            .weight(new BigDecimal("70.0"))
            .build();
    ReflectionTestUtils.setField(u, "id", USER_ID);
    ReflectionTestUtils.setField(u, "diabetesType", DiabetesType.T2D);
    return u;
  }

  private static Food foodOnlyCarbs(int id, String name, String carbs) {
    Food f =
        Food.builder()
            .name(name)
            .carbsG(new BigDecimal(carbs))
            .searchCount(0)
            .cachedAt(java.time.LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(f, "id", id);
    return f;
  }

  private static GlucosePredictResponse aiResp() {
    return new GlucosePredictResponse(List.of(), 165.0, 50, "base", 0.85);
  }

  private static GlucosePrediction savedPrediction() {
    GlucosePrediction p = GlucosePrediction.builder().foodId(50).foodName("비빔밥").build();
    ReflectionTestUtils.setField(p, "id", 999);
    return p;
  }

  @Test
  void predictForFood_protein_fat_kcal_sugar_NULL이어도_AI_호출_성공() {
    // 회귀 방지선: 향후 buildAiRequest 가 protein/fat/kcal 를 사용하기 시작하면 NPE 로 즉시 감지.
    Food food = foodOnlyCarbs(50, "비빔밥", "32.5");
    given(tx.findUser(USER_ID)).willReturn(user());
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(
            tx.savePrediction(
                any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class)))
        .willReturn(savedPrediction());

    assertThatCode(() -> predictionService.predictForFood(USER_ID, food))
        .doesNotThrowAnyException();

    ArgumentCaptor<GlucosePredictRequest> aiCaptor =
        ArgumentCaptor.forClass(GlucosePredictRequest.class);
    verify(glucosePredictClient).predict(aiCaptor.capture());
    GlucosePredictRequest aiRequest = aiCaptor.getValue();
    assertThat(aiRequest.meal().carbs()).isEqualTo(32.5);
    assertThat(aiRequest.userProfile().weightKg()).isEqualTo(70.0);
  }

  @Test
  void predictForFood_glucose_predictions에_foodId_foodName_저장() {
    Food food = foodOnlyCarbs(60, "김치_배추", "5.0");
    given(tx.findUser(USER_ID)).willReturn(user());
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(
            tx.savePrediction(
                any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class)))
        .willReturn(savedPrediction());

    predictionService.predictForFood(USER_ID, food);

    ArgumentCaptor<PredictRequest> reqCaptor = ArgumentCaptor.forClass(PredictRequest.class);
    verify(tx)
        .savePrediction(any(Integer.class), reqCaptor.capture(), any(GlucosePredictResponse.class));
    PredictRequest captured = reqCaptor.getValue();
    assertThat(captured.foodId()).isEqualTo(60);
    assertThat(captured.foodName()).isEqualTo("김치_배추");
  }

  @Test
  void comparePredict_A와_B_둘다_AI_호출되고_응답_합쳐짐() {
    // Mock Executor 는 execute 호출이 no-op — future.join() 영구 블록 회피하려 caller 스레드 동기 실행으로 stub.
    // 본 패턴: 새 comparePredict 테스트 추가 시에도 동일.
    doAnswer(
            inv -> {
              ((Runnable) inv.getArgument(0)).run();
              return null;
            })
        .when(asyncExecutor)
        .execute(any(Runnable.class));

    given(tx.findUser(USER_ID)).willReturn(user());
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(
            tx.savePrediction(
                any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class)))
        .willReturn(savedPrediction());

    PredictRequest reqA =
        new PredictRequest(
            50,
            "비빔밥",
            new BigDecimal("32.5"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            null,
            new BigDecimal("0"),
            null,
            null);
    PredictRequest reqB =
        new PredictRequest(
            60,
            "김밥",
            new BigDecimal("45.0"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            null,
            new BigDecimal("0"),
            null,
            null);
    AbPredictResponse response =
        predictionService.comparePredict(USER_ID, new AbPredictRequest(reqA, reqB));

    assertThat(response.foodA()).isNotNull();
    assertThat(response.foodB()).isNotNull();
    verify(asyncExecutor, times(2)).execute(any(Runnable.class));
    verify(glucosePredictClient, times(2)).predict(any(GlucosePredictRequest.class));
    verify(tx, times(2))
        .savePrediction(
            any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class));
  }

  @Test
  void predict_AI_호출은_findUser_와_savePrediction_사이에_실행() {
    // tx 분리 검증: AI 호출이 findUser tx 와 savePrediction tx 사이 — DB 커넥션 점유 안 함을 mock 호출 순서로 시각화.
    Food food = foodOnlyCarbs(70, "비빔밥", "32.5");
    given(tx.findUser(USER_ID)).willReturn(user());
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(
            tx.savePrediction(
                any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class)))
        .willReturn(savedPrediction());

    predictionService.predictForFood(USER_ID, food);

    var inOrder = org.mockito.Mockito.inOrder(tx, glucosePredictClient);
    inOrder.verify(tx).findUser(eq(USER_ID));
    inOrder.verify(glucosePredictClient).predict(any(GlucosePredictRequest.class));
    inOrder
        .verify(tx)
        .savePrediction(
            any(Integer.class), any(PredictRequest.class), any(GlucosePredictResponse.class));
  }
}
