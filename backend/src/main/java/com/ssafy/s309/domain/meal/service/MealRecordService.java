package com.ssafy.s309.domain.meal.service;

import com.ssafy.s309.common.service.S3Service;
import com.ssafy.s309.domain.agent.entity.AgentPendingTrigger;
import com.ssafy.s309.domain.agent.repository.AgentPendingTriggerRepository;
import com.ssafy.s309.domain.meal.dto.MealRecordCreateRequest;
import com.ssafy.s309.domain.meal.dto.MealRecordCreateResponse;
import com.ssafy.s309.domain.meal.dto.MealRecordResponse;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MealRecordService {

  private static final int TRIGGER_DELAY_MINUTES = 1;

  private final MealRecordRepository mealRecordRepository;
  private final AgentPendingTriggerRepository triggerRepository;
  private final S3Service s3Service;

  @Transactional
  public MealRecordCreateResponse create(
      Integer userId, MealRecordCreateRequest request, MultipartFile image) {
    String imageStorageKey = null;
    String imageOriginName = null;

    if (image != null && !image.isEmpty()) {
      try {
        imageStorageKey = s3Service.upload(image, "meals");
        imageOriginName = image.getOriginalFilename();
      } catch (Exception e) {
        throw new IllegalStateException("이미지 업로드에 실패했습니다", e);
      }
    }

    MealRecord meal =
        mealRecordRepository.save(
            MealRecord.builder()
                .userId(userId)
                .foodId(request.foodId())
                .memo(request.memo())
                .recordedAt(request.recordedAt())
                .imageStorageKey(imageStorageKey)
                .imageOriginName(imageOriginName)
                .isProcessed(false)
                .build());

    triggerRepository.save(
        AgentPendingTrigger.builder()
            .userId(userId)
            .triggerType(AgentPendingTrigger.TYPE_POST_MEAL)
            .referenceId(meal.getId())
            .scheduledAt(request.recordedAt().plusMinutes(TRIGGER_DELAY_MINUTES))
            .build());

    return new MealRecordCreateResponse(meal.getId());
  }

  @Transactional(readOnly = true)
  public List<MealRecordResponse> getByDate(Integer userId, LocalDate date) {
    LocalDateTime from = date.atStartOfDay();
    LocalDateTime to = date.atTime(LocalTime.MAX);

    return mealRecordRepository.findWithFoodByUserIdAndRecordedAtBetween(userId, from, to).stream()
        .map(
            meal -> {
              String imageUrl =
                  meal.getImageStorageKey() != null
                      ? s3Service.getPresignedDownloadUrl(meal.getImageStorageKey())
                      : null;
              return MealRecordResponse.from(meal, imageUrl);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MealRecordResponse> getByFoodId(Integer userId, Integer foodId) {
    return mealRecordRepository.findWithFoodByUserIdAndFoodId(userId, foodId).stream()
        .map(
            meal -> {
              String imageUrl =
                  meal.getImageStorageKey() != null
                      ? s3Service.getPresignedDownloadUrl(meal.getImageStorageKey())
                      : null;
              return MealRecordResponse.from(meal, imageUrl);
            })
        .toList();
  }
}
