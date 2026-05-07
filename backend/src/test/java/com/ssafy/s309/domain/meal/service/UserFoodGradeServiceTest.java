package com.ssafy.s309.domain.meal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.meal.dto.FoodGradeResponse;
import com.ssafy.s309.domain.meal.repository.UserFoodGradeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class UserFoodGradeServiceTest {

  @Mock private UserFoodGradeRepository userFoodGradeRepository;
  @InjectMocks private UserFoodGradeService userFoodGradeService;

  private static final Integer USER_ID = 1;

  private FoodGradeResponse sampleResponse(String grade, BigDecimal slope, int count) {
    return new FoodGradeResponse(10, "테스트음식", grade, slope, count, LocalDateTime.now());
  }

  @Test
  void 음식_성적표_목록을_반환한다() {
    List<FoodGradeResponse> expected =
        List.of(
            sampleResponse("S", new BigDecimal("0.3"), 8),
            sampleResponse("A", new BigDecimal("0.8"), 12),
            sampleResponse("C", new BigDecimal("1.5"), 3));
    given(userFoodGradeRepository.findGradesByUserId(USER_ID)).willReturn(expected);

    List<FoodGradeResponse> result = userFoodGradeService.getMyFoodGrades(USER_ID);

    assertThat(result).hasSize(3);
    assertThat(result).isEqualTo(expected);
    verify(userFoodGradeRepository).findGradesByUserId(USER_ID);
  }

  @Test
  void 음식이_없으면_빈_리스트를_반환한다() {
    given(userFoodGradeRepository.findGradesByUserId(USER_ID)).willReturn(List.of());

    List<FoodGradeResponse> result = userFoodGradeService.getMyFoodGrades(USER_ID);

    assertThat(result).isEmpty();
  }
}
