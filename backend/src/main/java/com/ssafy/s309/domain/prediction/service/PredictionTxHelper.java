package com.ssafy.s309.domain.prediction.service;

import com.ssafy.s309.domain.prediction.client.dto.GlucosePredictResponse;
import com.ssafy.s309.domain.prediction.dto.PredictRequest;
import com.ssafy.s309.domain.prediction.entity.GlucosePrediction;
import com.ssafy.s309.domain.prediction.repository.GlucosePredictionRepository;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PredictionService 의 tx 경계 분리용 helper.
 *
 * <p>AI 호출(GlucosePredictClient)을 트랜잭션 밖에서 수행하기 위해 DB 조회/쓰기 책임만 분리. 짧은 tx 로 격리해 외부 호출 응답 대기 동안 DB
 * 커넥션 점유를 막는다.
 */
@Component
@RequiredArgsConstructor
class PredictionTxHelper {

  private final UserRepository userRepository;
  private final GlucosePredictionRepository predictionRepository;

  @Transactional(readOnly = true)
  public User findUser(Integer userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저: " + userId));
  }

  @Transactional
  public GlucosePrediction savePrediction(
      User user, PredictRequest request, GlucosePredictResponse aiResponse) {
    return predictionRepository.save(
        GlucosePrediction.builder()
            .user(user)
            .foodId(request.foodId())
            .foodName(request.foodName())
            .predictedCurve(aiResponse.curve())
            .predictedPeak(
                aiResponse.peakMgdl() != null ? BigDecimal.valueOf(aiResponse.peakMgdl()) : null)
            .build());
  }
}
