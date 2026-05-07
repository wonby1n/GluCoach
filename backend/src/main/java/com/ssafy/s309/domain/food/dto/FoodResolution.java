package com.ssafy.s309.domain.food.dto;

import com.ssafy.s309.domain.food.entity.Food;

/**
 * CV 인식 음식명을 foods 테이블의 row로 해석한 결과.
 *
 * <p>{@link ResolutionStatus#PENDING_NUTRITION} 인 경우 {@code food.carbsG} 가 NULL — 호출자는 사용자에게 탄수화물
 * 직접 입력을 요청하거나 빈 prediction 으로 응답해야 한다.
 */
public record FoodResolution(Food food, ResolutionStatus status) {

  public enum ResolutionStatus {
    /** DB 캐시에 영양정보가 있는 row 가 존재. */
    CACHE_HIT,
    /** 식약처 API 호출 성공 + upsert 로 영양정보 확보. */
    REMOTE_FETCHED,
    /** DB·API 모두 영양정보 미스 → customized=true row 신규 생성 (영양정보 NULL). */
    PENDING_NUTRITION
  }
}
