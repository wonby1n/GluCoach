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

  /**
   * 새 tx 안에서 User reference (proxy) 를 만들어 GlucosePrediction.user 에 연결.
   *
   * <p>호출자가 detached User 객체를 그대로 association 으로 넘기면 cascade 설정 변경 시 silent regression
   * (PersistentObjectException / LazyInitializationException) 위험이 있음. userId 만 받고 새 tx 안에서 proxy 를
   * 발급받아 안전하게 영속화. comparePredict 의 두 async worker 도 각자 자체 proxy 를 갖게 되어 동시성 자동 안전.
   */
  @Transactional
  public GlucosePrediction savePrediction(
      Integer userId, PredictRequest request, GlucosePredictResponse aiResponse) {
    User userRef = userRepository.getReferenceById(userId);
    return predictionRepository.save(
        GlucosePrediction.builder()
            .user(userRef)
            .foodId(request.foodId())
            .foodName(request.foodName())
            .predictedCurve(aiResponse.curve())
            .predictedPeak(
                aiResponse.peakMgdl() != null ? BigDecimal.valueOf(aiResponse.peakMgdl()) : null)
            .build());
  }
}
