package com.ssafy.s309.domain.meal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.meal.entity.MealGlucoseResponse;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import com.ssafy.s309.domain.meal.entity.UserFoodGrade;
import com.ssafy.s309.domain.meal.repository.MealGlucoseResponseRepository;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.meal.repository.UserFoodGradeRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class MealGlucoseResponseServiceTest {

  @Mock private MealRecordRepository mealRecordRepository;
  @Mock private GlucoseRecordRepository glucoseRecordRepository;
  @Mock private MealGlucoseResponseRepository mealGlucoseResponseRepository;
  @Mock private UserFoodGradeRepository userFoodGradeRepository;
  @InjectMocks private MealGlucoseResponseService mealGlucoseResponseService;

  private static final Integer MEAL_ID = 1;
  private static final Integer USER_ID = 10;
  private static final Integer FOOD_ID = 20;
  private static final LocalDateTime MEAL_TIME = LocalDateTime.of(2026, 5, 6, 12, 0);

  private MealRecord meal;
  private GlucoseRecord baseline;
  private GlucoseRecord peak;

  @BeforeEach
  void setUp() {
    meal =
        MealRecord.builder()
            .userId(USER_ID)
            .foodId(FOOD_ID)
            .isProcessed(false)
            .recordedAt(MEAL_TIME)
            .build();
    ReflectionTestUtils.setField(meal, "id", MEAL_ID);

    // baseline: 식사 5분 전, 90 mg/dL
    baseline =
        GlucoseRecord.builder()
            .userId(USER_ID)
            .value(new BigDecimal("90.00"))
            .measuredAt(MEAL_TIME.minusMinutes(5))
            .build();
    ReflectionTestUtils.setField(baseline, "id", 101L);

    // peak: 식사 60분 후, 150 mg/dL → slope = (150-90)/60 = 1.0
    peak =
        GlucoseRecord.builder()
            .userId(USER_ID)
            .value(new BigDecimal("150.00"))
            .measuredAt(MEAL_TIME.plusMinutes(60))
            .build();
    ReflectionTestUtils.setField(peak, "id", 102L);
  }

  // ── findUnprocessedMealIds ─────────────────────────────────────────────

  @Test
  void findUnprocessedMealIds_미처리_식사_ID_목록_반환() {
    MealRecord meal2 =
        MealRecord.builder().userId(USER_ID).isProcessed(false).recordedAt(MEAL_TIME).build();
    ReflectionTestUtils.setField(meal2, "id", 2);

    given(mealRecordRepository.findUnprocessedBefore(any(LocalDateTime.class)))
        .willReturn(List.of(meal, meal2));

    List<Integer> result = mealGlucoseResponseService.findUnprocessedMealIds();

    assertThat(result).containsExactly(MEAL_ID, 2);
  }

  @Test
  void findUnprocessedMealIds_미처리_식사_없으면_빈_리스트() {
    given(mealRecordRepository.findUnprocessedBefore(any(LocalDateTime.class)))
        .willReturn(List.of());

    assertThat(mealGlucoseResponseService.findUnprocessedMealIds()).isEmpty();
  }

  // ── processMeal — 스킵 케이스 ──────────────────────────────────────────

  @Test
  void processMeal_이미_처리된_meal은_스킵() {
    MealRecord processed =
        MealRecord.builder().userId(USER_ID).isProcessed(true).recordedAt(MEAL_TIME).build();
    ReflectionTestUtils.setField(processed, "id", MEAL_ID);
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(processed));

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(glucoseRecordRepository, never())
        .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(any(), any(), any());
    verify(mealGlucoseResponseRepository, never()).save(any());
  }

  @Test
  void processMeal_meal_없으면_조용히_종료() {
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(mealGlucoseResponseRepository, never()).save(any());
  }

  @Test
  void processMeal_response_존재하나_isProcessed_false면_복구() {
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(meal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(true);

    mealGlucoseResponseService.processMeal(MEAL_ID);

    assertThat(meal.getIsProcessed()).isTrue();
    verify(glucoseRecordRepository, never())
        .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(any(), any(), any());
  }

  @Test
  void processMeal_baseline_혈당_없으면_스킵() {
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(meal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(mealGlucoseResponseRepository, never()).save(any());
    assertThat(meal.getIsProcessed()).isFalse();
  }

  @Test
  void processMeal_식후_혈당_데이터_없으면_스킵() {
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(meal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of(baseline));
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.plusMinutes(1)), eq(MEAL_TIME.plusMinutes(120))))
        .willReturn(List.of());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(mealGlucoseResponseRepository, never()).save(any());
    assertThat(meal.getIsProcessed()).isFalse();
  }

  // ── processMeal — 정상 처리 ─────────────────────────────────────────────

  @Test
  void processMeal_정상처리_response_저장_and_isProcessed_true() {
    stubHappyPath();
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    ArgumentCaptor<MealGlucoseResponse> captor = ArgumentCaptor.forClass(MealGlucoseResponse.class);
    verify(mealGlucoseResponseRepository).save(captor.capture());
    MealGlucoseResponse saved = captor.getValue();
    assertThat(saved.getMealId()).isEqualTo(MEAL_ID);
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getBaselineGlucoseId()).isEqualTo(101L);
    assertThat(saved.getPeakGlucoseId()).isEqualTo(102L);
    assertThat(meal.getIsProcessed()).isTrue();
  }

  @Test
  void processMeal_slope_계산_정확성() {
    // (150 - 90) / 60분 = 1.0 mg/dL/min
    stubHappyPath();
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    ArgumentCaptor<MealGlucoseResponse> captor = ArgumentCaptor.forClass(MealGlucoseResponse.class);
    verify(mealGlucoseResponseRepository).save(captor.capture());
    assertThat(captor.getValue().getSlope()).isEqualByComparingTo(new BigDecimal("1.0"));
  }

  @Test
  void processMeal_peak는_최고혈당_기준으로_선택() {
    // 여러 post 측정값 중 최고값을 peak로 선택해야 함
    GlucoseRecord mid =
        GlucoseRecord.builder()
            .userId(USER_ID)
            .value(new BigDecimal("130.00"))
            .measuredAt(MEAL_TIME.plusMinutes(30))
            .build();
    ReflectionTestUtils.setField(mid, "id", 103L);

    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(meal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of(baseline));
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.plusMinutes(1)), eq(MEAL_TIME.plusMinutes(120))))
        .willReturn(List.of(mid, peak)); // mid=130, peak=150
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    ArgumentCaptor<MealGlucoseResponse> captor = ArgumentCaptor.forClass(MealGlucoseResponse.class);
    verify(mealGlucoseResponseRepository).save(captor.capture());
    assertThat(captor.getValue().getPeakGlucoseId()).isEqualTo(102L); // peak(150)의 id
  }

  // ── processMeal — food grade upsert ────────────────────────────────────

  @Test
  void processMeal_foodId_있으면_신규_grade_생성() {
    stubHappyPath();
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    ArgumentCaptor<UserFoodGrade> captor = ArgumentCaptor.forClass(UserFoodGrade.class);
    verify(userFoodGradeRepository).save(captor.capture());
    UserFoodGrade created = captor.getValue();
    // slope = 1.0 → grade A (새 기준: S≤0.5 / A≤1.0 / B≤1.4 / C≤1.7 / D>1.7)
    assertThat(created.getGrade()).isEqualTo("A");
    assertThat(created.getMealCount()).isEqualTo(1);
    assertThat(created.getAvgSlope()).isEqualByComparingTo(new BigDecimal("1.0"));
    assertThat(created.getUserId()).isEqualTo(USER_ID);
    assertThat(created.getFoodId()).isEqualTo(FOOD_ID);
  }

  @Test
  void processMeal_foodId_있으면_grade_평균_업데이트() {
    // existing: avgSlope=0.5, mealCount=2
    // new slope=1.0 → newAvg=(0.5*2+1.0)/3=2.0/3=0.7 → grade A (0.7 ≤ 1.0)
    UserFoodGrade existing =
        UserFoodGrade.builder()
            .userId(USER_ID)
            .foodId(FOOD_ID)
            .avgSlope(new BigDecimal("0.5"))
            .grade("A")
            .mealCount(2)
            .build();

    stubHappyPath();
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.of(existing));

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(userFoodGradeRepository, never()).save(any()); // 신규 save 없음 (dirty checking으로 처리)
    assertThat(existing.getMealCount()).isEqualTo(3);
    assertThat(existing.getAvgSlope()).isEqualByComparingTo(new BigDecimal("0.7"));
    assertThat(existing.getGrade()).isEqualTo("A");
  }

  @Test
  void processMeal_foodId_없으면_grade_upsert_안함() {
    MealRecord noFoodMeal =
        MealRecord.builder()
            .userId(USER_ID)
            .foodId(null)
            .isProcessed(false)
            .recordedAt(MEAL_TIME)
            .build();
    ReflectionTestUtils.setField(noFoodMeal, "id", MEAL_ID);

    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(noFoodMeal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of(baseline));
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.plusMinutes(1)), eq(MEAL_TIME.plusMinutes(120))))
        .willReturn(List.of(peak));

    mealGlucoseResponseService.processMeal(MEAL_ID);

    verify(userFoodGradeRepository, never()).findByUserIdAndFoodId(any(), any());
    verify(userFoodGradeRepository, never()).save(any());
  }

  // ── slope → grade 경계값 ────────────────────────────────────────────────

  // 등급 기준: S≤0.5 / A≤1.0 / B≤1.4 / C≤1.7 / D>1.7

  @Test
  void slope_0_5이하_S등급() {
    // (120-90)/60 = 30/60 = 0.5 → S
    assertGradeForPeak(new BigDecimal("120.00"), 60, "S");
  }

  @Test
  void slope_1_0이하_A등급() {
    // (150-90)/60 = 60/60 = 1.0 → A
    assertGradeForPeak(new BigDecimal("150.00"), 60, "A");
  }

  @Test
  void slope_1_4이하_B등급() {
    // (174-90)/60 = 84/60 = 1.4 → B
    assertGradeForPeak(new BigDecimal("174.00"), 60, "B");
  }

  @Test
  void slope_1_7이하_C등급() {
    // (192-90)/60 = 102/60 = 1.7 → C
    assertGradeForPeak(new BigDecimal("192.00"), 60, "C");
  }

  @Test
  void slope_1_7초과_D등급() {
    // (210-90)/60 = 120/60 = 2.0 → D
    assertGradeForPeak(new BigDecimal("210.00"), 60, "D");
  }

  // ── 헬퍼 ───────────────────────────────────────────────────────────────

  private void stubHappyPath() {
    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(meal));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of(baseline));
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.plusMinutes(1)), eq(MEAL_TIME.plusMinutes(120))))
        .willReturn(List.of(peak));
  }

  private void assertGradeForPeak(
      BigDecimal peakValue, int minutesAfterMeal, String expectedGrade) {
    MealRecord m =
        MealRecord.builder()
            .userId(USER_ID)
            .foodId(FOOD_ID)
            .isProcessed(false)
            .recordedAt(MEAL_TIME)
            .build();
    ReflectionTestUtils.setField(m, "id", MEAL_ID);

    GlucoseRecord b =
        GlucoseRecord.builder()
            .userId(USER_ID)
            .value(new BigDecimal("90.00"))
            .measuredAt(MEAL_TIME.minusMinutes(5))
            .build();
    ReflectionTestUtils.setField(b, "id", 200L);

    GlucoseRecord p =
        GlucoseRecord.builder()
            .userId(USER_ID)
            .value(peakValue)
            .measuredAt(MEAL_TIME.plusMinutes(minutesAfterMeal))
            .build();
    ReflectionTestUtils.setField(p, "id", 201L);

    given(mealRecordRepository.findById(MEAL_ID)).willReturn(Optional.of(m));
    given(mealGlucoseResponseRepository.existsByMealId(MEAL_ID)).willReturn(false);
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.minusMinutes(60)), eq(MEAL_TIME)))
        .willReturn(List.of(b));
    given(
            glucoseRecordRepository.findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
                eq(USER_ID), eq(MEAL_TIME.plusMinutes(1)), eq(MEAL_TIME.plusMinutes(120))))
        .willReturn(List.of(p));
    given(userFoodGradeRepository.findByUserIdAndFoodId(USER_ID, FOOD_ID))
        .willReturn(Optional.empty());

    mealGlucoseResponseService.processMeal(MEAL_ID);

    ArgumentCaptor<UserFoodGrade> captor = ArgumentCaptor.forClass(UserFoodGrade.class);
    verify(userFoodGradeRepository).save(captor.capture());
    assertThat(captor.getValue().getGrade()).isEqualTo(expectedGrade);
  }
}
