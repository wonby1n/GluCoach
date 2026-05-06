package com.ssafy.s309.domain.meal.scheduler;

import com.ssafy.s309.domain.meal.service.MealGlucoseResponseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MealGlucoseResponseScheduler {

  private final MealGlucoseResponseService mealGlucoseResponseService;

  @Scheduled(fixedDelay = 300_000)
  public void processPendingMealResponses() {
    List<Integer> mealIds = mealGlucoseResponseService.findUnprocessedMealIds();
    if (mealIds.isEmpty()) {
      return;
    }
    log.debug("식후 혈당 반응 처리 대상 {}건", mealIds.size());

    for (Integer mealId : mealIds) {
      try {
        // 스케줄러에서 서비스 프록시를 통해 호출 → 각 meal이 독립적인 @Transactional 적용
        mealGlucoseResponseService.processMeal(mealId);
      } catch (Exception e) {
        log.warn("식후 혈당 반응 처리 실패 mealId={}: {}", mealId, e.getMessage());
      }
    }
  }
}
