package com.ssafy.s309.domain.timeline.service;

import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.timeline.dto.ExercisePin;
import com.ssafy.s309.domain.timeline.dto.GlucosePoint;
import com.ssafy.s309.domain.timeline.dto.MealPin;
import com.ssafy.s309.domain.timeline.dto.SleepPin;
import com.ssafy.s309.domain.timeline.dto.TimelineRange;
import com.ssafy.s309.domain.timeline.dto.TimelineResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimelineService {

  private final GlucoseRecordRepository glucoseRepo;
  private final MealRecordRepository mealRepo;
  private final Executor asyncExecutor;

  @Transactional(readOnly = true)
  public TimelineResponse getTimeline(Integer userId, TimelineRange range) {
    LocalDateTime to = LocalDateTime.now();
    LocalDateTime from = range.computeFrom(to);

    CompletableFuture<List<GlucosePoint>> glucoseFuture =
        CompletableFuture.supplyAsync(
            () ->
                glucoseRepo
                    .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, from, to)
                    .stream()
                    .map(GlucosePoint::from)
                    .toList(),
            asyncExecutor);

    CompletableFuture<List<MealPin>> mealFuture =
        CompletableFuture.supplyAsync(
            () ->
                mealRepo
                    .findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(userId, from, to)
                    .stream()
                    .map(MealPin::from)
                    .toList(),
            asyncExecutor);

    List<ExercisePin> exercises = List.of();
    List<SleepPin> sleeps = List.of();

    return new TimelineResponse(
        range.token(), from, to, glucoseFuture.join(), mealFuture.join(), exercises, sleeps);
  }
}
