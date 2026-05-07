package com.ssafy.s309.domain.weekly_report.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.common.service.S3Service;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiResponse;
import com.ssafy.s309.domain.weekly_report.dto.projection.WeeklyFoodItemProjection;
import com.ssafy.s309.domain.weekly_report.entity.WeeklyFood;
import com.ssafy.s309.domain.weekly_report.entity.WeeklyReport;
import com.ssafy.s309.domain.weekly_report.repository.WeeklyFoodRepository;
import com.ssafy.s309.domain.weekly_report.repository.WeeklyReportRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

  private static final String REPORT_FCM_CHANNEL = "report_notification";
  private static final String REPORT_ALERT_TYPE = "WEEKLY_REPORT";

  private final WeeklyReportQueryService queryService;
  private final WeeklyReportAiClient aiClient;
  private final WeeklyReportRepository weeklyReportRepository;
  private final WeeklyFoodRepository weeklyFoodRepository;
  private final MealRecordRepository mealRecordRepository;
  private final NotificationTokenRepository notificationTokenRepository;
  private final S3Service s3Service;
  private final FcmService fcmService;

  /** 단일 유저의 주간 보고서를 생성한다. 스케줄러가 각 유저에 대해 호출하며, 독립 트랜잭션으로 실행되어 한 유저 실패가 다른 유저에 영향을 주지 않는다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void generateForUser(User user, LocalDate weekStart) {
    if (weeklyReportRepository.existsByUserIdAndWeekStart(user.getId(), weekStart)) {
      log.info("주간 보고서 이미 존재, 스킵: userId={} week={}", user.getId(), weekStart);
      return;
    }

    log.info("주간 보고서 생성 시작: userId={} week={}", user.getId(), weekStart);

    // ── 1. DB 집계 → AI 요청 조립 ──────────────────────────────
    WeeklyReportAiRequest aiRequest = queryService.buildAiRequest(user, weekStart);

    // ── 2. AI 호출 (LLM + PDF 생성) ───────────────────────────
    WeeklyReportAiResponse aiResponse = aiClient.generate(aiRequest);

    // ── 3. PDF bytes 디코딩 → S3 업로드 ──────────────────────
    byte[] pdfBytes = Base64.getDecoder().decode(aiResponse.getPdfBytes());
    String pdfKey = "reports/" + user.getId() + "/week_" + weekStart + ".pdf";
    s3Service.uploadBytes(pdfBytes, pdfKey, "application/pdf");

    // ── 4. weekly_reports 저장 ────────────────────────────────
    WeeklyReport report =
        WeeklyReport.builder()
            .userId(user.getId())
            .weekStart(weekStart)
            .avgGlucose(aiRequest.getAvgGlucose())
            .minGlucose(aiRequest.getMinGlucose())
            .maxGlucose(aiRequest.getMaxGlucose())
            .glucoseSd(aiRequest.getGlucoseSd())
            .timeInRange(aiRequest.getTimeInRange())
            .timeAboveRange(aiRequest.getTimeAboveRange())
            .timeBelowRange(aiRequest.getTimeBelowRange())
            .aiSummary(aiResponse.getAiSummary())
            .aiSuggest(aiResponse.getAiSuggest())
            .pdfKey(pdfKey)
            .build();
    WeeklyReport saved = weeklyReportRepository.save(report);

    // ── 5. weekly_foods 저장 ──────────────────────────────────
    LocalDate weekEnd = weekStart.plusDays(6);
    LocalDateTime from = weekStart.atStartOfDay();
    LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();

    List<WeeklyFood> foods = new ArrayList<>();
    mealRecordRepository
        .findWeeklyGoodFoods(user.getId(), from, to)
        .forEach(p -> foods.add(toWeeklyFood(saved.getId(), p, "GOOD")));
    mealRecordRepository
        .findWeeklyBadFoods(user.getId(), from, to)
        .forEach(p -> foods.add(toWeeklyFood(saved.getId(), p, "BAD")));
    weeklyFoodRepository.saveAll(foods);

    // ── 6. FCM 알림 ───────────────────────────────────────────
    List<String> tokens =
        notificationTokenRepository.findByUser_IdAndIsActiveTrue(user.getId()).stream()
            .map(t -> t.getToken())
            .toList();

    if (!tokens.isEmpty()) {
      fcmService.sendToTokens(
          tokens, "주간 보고서 완성", "이번 주 혈당 리포트가 준비됐어요!", REPORT_FCM_CHANNEL, REPORT_ALERT_TYPE);
    }

    log.info("주간 보고서 생성 완료: userId={} reportId={} pdfKey={}", user.getId(), saved.getId(), pdfKey);
  }

  private WeeklyFood toWeeklyFood(Integer reportId, WeeklyFoodItemProjection p, String type) {
    return WeeklyFood.builder()
        .reportId(reportId)
        .foodId(p.getFoodId())
        .type(type)
        .avgSlope(p.getAvgSlope())
        .build();
  }
}
