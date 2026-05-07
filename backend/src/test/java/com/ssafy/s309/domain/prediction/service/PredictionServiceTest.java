package com.ssafy.s309.domain.prediction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.prediction.client.GlucosePredictClient;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictRequest;
import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.prediction.repository.GlucosePredictionRepository;
import com.ssafy.s309.domain.user.entity.DiabetesType;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
  @Mock private GlucosePredictionRepository predictionRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private PredictionService predictionService;

  private static final Integer USER_ID = 7;

  private static User user() {
    // Builder 가 diabetesType 을 노출하지 않아 reflection 으로 설정.
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
    // 영양정보 중 carbs 만 있고 protein/fat/kcal/sugar 모두 NULL — predictForFood 의 null pass-through 보장 검증.
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

  @Test
  void predictForFood_protein_fat_kcal_sugar_NULL이어도_AI_호출_성공() {
    // 회귀 방지선: 향후 buildAiRequest 나 savePrediction 이 protein/fat/kcal 를 사용하기 시작하면 이 테스트가 NPE 로 fail
    // 해서 즉시 감지된다. 현재는 carbs 만 사용하므로 통과해야 한다.
    Food food = foodOnlyCarbs(50, "비빔밥", "32.5");
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(predictionRepository.save(any(GlucosePrediction.class)))
        .willAnswer(inv -> inv.getArgument(0));

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
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
    given(glucosePredictClient.predict(any(GlucosePredictRequest.class))).willReturn(aiResp());
    given(predictionRepository.save(any(GlucosePrediction.class)))
        .willAnswer(inv -> inv.getArgument(0));

    predictionService.predictForFood(USER_ID, food);

    ArgumentCaptor<GlucosePrediction> saveCaptor = ArgumentCaptor.forClass(GlucosePrediction.class);
    verify(predictionRepository).save(saveCaptor.capture());
    GlucosePrediction saved = saveCaptor.getValue();
    assertThat(saved.getFoodId()).isEqualTo(60);
    assertThat(saved.getFoodName()).isEqualTo("김치_배추");
  }
}
