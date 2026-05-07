package com.ssafy.s309.domain.prediction.dto;

import com.ssafy.s309.domain.prediction.client.dto.FoodDetection;
import java.util.List;

/**
 * 사진 통합 식전 예측 응답 ({@code POST /api/predict/glucose/from-image}).
 *
 * <p>FE는 {@link Status} 로 분기:
 *
 * <ul>
 *   <li>{@code OK}: prediction 표시
 *   <li>{@code LOW_CONFIDENCE}: detected 목록을 사용자에게 보여주고 선택/텍스트 입력 fallback.
 *       requireConfirmation=true.
 *   <li>{@code PENDING_NUTRITION}: 사용자에게 탄수화물 직접 입력 요청. 입력 안 하면 빈 prediction 그대로 표시.
 * </ul>
 *
 * <p>{@code prediction} 은 OK 가 아닐 때 항상 null. {@code foodId/foodName} 은 LOW_CONFIDENCE 가 아닐 때 채워진다
 * (PENDING_NUTRITION 의 경우 customized=true 로 저장된 row 의 id).
 */
public record FromImagePredictResponse(
    Status status,
    List<FoodDetection> detected,
    Integer foodId,
    String foodName,
    PredictResponse prediction,
    boolean requireConfirmation) {

  public enum Status {
    OK,
    LOW_CONFIDENCE,
    PENDING_NUTRITION
  }
}
