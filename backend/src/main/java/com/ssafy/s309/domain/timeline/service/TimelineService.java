package com.ssafy.s309.domain.timeline.service;

import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.health.repository.ExerciseRecordRepository;
import com.ssafy.s309.domain.health.repository.SleepRecordRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimelineService {

  private final GlucoseRecordRepository glucoseRepo;
  private final MealRecordRepository mealRepo;
  private final ExerciseRecordRepository exerciseRepo;
  private final SleepRecordRepository sleepRepo;

  @Transactional(readOnly = true)
  public TimelineResponse getTimeline(Long userId, TimelineRange range) {
    LocalDateTime to = LocalDateTime.now();
    LocalDateTime from = range.computeFrom(to);

    CompletableFuture<List<GlucosePoint>> glucoseFuture =
        CompletableFuture.supplyAsync(
            () ->
                glucoseRepo
                    .findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId, from, to)
                    .stream()
                    .map(GlucosePoint::from)
                    .toList());

    CompletableFuture<List<MealPin>> mealFuture =
        CompletableFuture.supplyAsync(
            () ->
                mealRepo
                    .findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(userId, from, to)
                    .stream()
                    .map(MealPin::from)
                    .toList());

    CompletableFuture<List<ExercisePin>> exerciseFuture =
        CompletableFuture.supplyAsync(
            () ->
                exerciseRepo.findOverlappingByUserId(userId, from, to).stream()
                    .map(ExercisePin::from)
                    .toList());

    CompletableFuture<List<SleepPin>> sleepFuture =
        CompletableFuture.supplyAsync(
            () ->
                sleepRepo.findOverlappingByUserId(userId, from, to).stream()
                    .map(SleepPin::from)
                    .toList());

    return new TimelineResponse(
        range.token(),
        from,
        to,
        glucoseFuture.join(),
        mealFuture.join(),
        exerciseFuture.join(),
        sleepFuture.join());
  }
}
