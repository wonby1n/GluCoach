package com.ssafy.s309.domain.agent.service;

import com.ssafy.s309.domain.agent.dto.AgentFoodGradeItem;
import com.ssafy.s309.domain.agent.dto.AgentGlucoseRecentItem;
import com.ssafy.s309.domain.agent.dto.AgentMealItem;
import com.ssafy.s309.domain.agent.dto.AgentUnseenFoodItem;
import com.ssafy.s309.domain.agent.dto.AgentUserProfileItem;
import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.repository.GlucoseRecordRepository;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import com.ssafy.s309.domain.meal.entity.MealRecord;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.meal.repository.UserFoodGradeRepository;
import com.ssafy.s309.domain.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 음식 추천 agent용 사용자 데이터 read 서비스. 4개 메서드 모두 read-only, 외부 호출 없음.
 *
 * <p>X-Agent-Api-Key 인증 (SecurityConfig /api/agent/** chain).
 */
@Service
@RequiredArgsConstructor
public class AgentUserDataService {

  private final UserFoodGradeRepository foodGradeRepository;
  private final UserRepository userRepository;
  private final MealRecordRepository mealRecordRepository;
  private final GlucoseRecordRepository glucoseRecordRepository;
  private final FoodRepository foodRepository;

  @Transactional(readOnly = true)
  public List<AgentUnseenFoodItem> getUnseenFoodCandidates(Integer userId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    LocalDateTime since = LocalDateTime.now().minusDays(7);
    return foodRepository.findUnseenForAgent(userId, since, PageRequest.of(0, safeLimit));
  }

  @Transactional(readOnly = true)
  public List<AgentFoodGradeItem> getFoodGrades(Integer userId) {
    return foodGradeRepository.findAgentGradesWithImageByUserId(userId).stream()
        .map(
            g ->
                new AgentFoodGradeItem(
                    g.getFoodId(),
                    g.getFoodName(),
                    g.getGrade(),
                    g.getAvgSlope(),
                    g.getMealCount(),
                    g.getLatestMealImageKey()))
        .toList();
  }

  @Transactional(readOnly = true)
  public AgentUserProfileItem getProfile(Integer userId) {
    return userRepository
        .findById(userId)
        .map(AgentUserProfileItem::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
  }

  /** 최근 N일치 식사 기록. days는 1~14 사이로 클램프. 기존 findAgentMealsByUserAndRange 재활용. */
  @Transactional(readOnly = true)
  public List<AgentMealItem> getRecentMeals(Integer userId, int days) {
    int safeDays = Math.max(1, Math.min(days, 14));
    LocalDateTime to = LocalDateTime.now();
    LocalDateTime from = to.minusDays(safeDays);
    return mealRecordRepository.findAgentMealsByUserAndRange(userId, from, to);
  }

  @Transactional(readOnly = true)
  public AgentGlucoseRecentItem getGlucoseRecent(Integer userId) {
    Optional<GlucoseRecord> latest =
        glucoseRecordRepository.findFirstByUserIdOrderByMeasuredAtDesc(userId);
    Optional<MealRecord> lastMeal =
        mealRecordRepository.findFirstByUserIdOrderByRecordedAtDesc(userId);

    Long lastMealMinAgo =
        lastMeal
            .map(m -> Duration.between(m.getRecordedAt(), LocalDateTime.now()).toMinutes())
            .orElse(null);

    return new AgentGlucoseRecentItem(
        latest.map(GlucoseRecord::getValue).orElse(null),
        latest.map(GlucoseRecord::getMeasuredAt).orElse(null),
        lastMealMinAgo);
  }
}
