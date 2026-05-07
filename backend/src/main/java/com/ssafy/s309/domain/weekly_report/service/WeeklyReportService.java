package com.ssafy.s309.domain.weekly_report.service;

import com.ssafy.s309.common.service.FcmService;
import com.ssafy.s309.common.service.S3Service;
import com.ssafy.s309.domain.meal.repository.MealRecordRepository;
import com.ssafy.s309.domain.notification.repository.NotificationTokenRepository;
import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiResponse;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportResponse;
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
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // ── 1. 음식 데이터 조회 (buildAiRequest + weekly_foods 저장에 재사용) ──
    LocalDate weekEnd = weekStart.plusDays(6);
    LocalDateTime from = weekStart.atStartOfDay();
    LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();

    List<WeeklyFoodItemProjection> goodProjections =
        mealRecordRepository.findWeeklyGoodFoods(user.getId(), from, to);
    List<WeeklyFoodItemProjection> badProjections =
        mealRecordRepository.findWeeklyBadFoods(user.getId(), from, to);

    // ── 2. DB 집계 → AI 요청 조립 ──────────────────────────────
    WeeklyReportAiRequest aiRequest =
        queryService.buildAiRequest(user, weekStart, goodProjections, badProjections);

    // ── 3. AI 호출 (LLM + PDF 생성) ───────────────────────────
    WeeklyReportAiResponse aiResponse = aiClient.generate(aiRequest);

    // ── 4. PDF bytes 디코딩 → S3 업로드 ──────────────────────
    byte[] pdfBytes = Base64.getDecoder().decode(aiResponse.getPdfBytes());
    String pdfKey = "reports/" + user.getId() + "/week_" + weekStart + ".pdf";
    s3Service.uploadBytes(pdfBytes, pdfKey, "application/pdf");

    // ── 5. weekly_reports / weekly_foods 저장 ────────────────
    // S3 업로드 이후 DB 저장 실패 시 고아 객체가 남지 않도록 S3 cleanup 보장
    try {
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

      List<WeeklyFood> foods = new ArrayList<>();
      goodProjections.forEach(p -> foods.add(toWeeklyFood(saved.getId(), p, "GOOD")));
      badProjections.forEach(p -> foods.add(toWeeklyFood(saved.getId(), p, "BAD")));
      weeklyFoodRepository.saveAll(foods);

      // ── 6. FCM 알림 ─────────────────────────────────────────
      List<String> tokens =
          notificationTokenRepository.findByUser_IdAndIsActiveTrue(user.getId()).stream()
              .map(t -> t.getToken())
              .toList();
      if (!tokens.isEmpty()) {
        fcmService.sendToTokens(
            tokens, "주간 보고서 완성", "이번 주 혈당 리포트가 준비됐어요!", REPORT_FCM_CHANNEL, REPORT_ALERT_TYPE);
      }

      log.info(
          "주간 보고서 생성 완료: userId={} reportId={} pdfKey={}", user.getId(), saved.getId(), pdfKey);

    } catch (Exception e) {
      log.warn("DB 저장 실패, S3 PDF 삭제 시도: pdfKey={}", pdfKey);
      try {
        s3Service.delete(pdfKey);
      } catch (Exception s3e) {
        log.warn("S3 PDF 삭제 실패 (수동 정리 필요): pdfKey={} cause={}", pdfKey, s3e.getMessage());
      }
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public List<WeeklyReportResponse> findAllByUserId(Integer userId) {
    List<WeeklyReport> reports = weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId);
    if (reports.isEmpty()) {
      return List.of();
    }
    List<Integer> reportIds = reports.stream().map(WeeklyReport::getId).toList();
    Map<Integer, List<WeeklyFood>> foodsByReportId =
        weeklyFoodRepository.findByReportIdInWithFood(reportIds).stream()
            .collect(Collectors.groupingBy(WeeklyFood::getReportId));
    return reports.stream()
        .map(r -> WeeklyReportResponse.from(r, foodsByReportId.getOrDefault(r.getId(), List.of())))
        .toList();
  }

  @Transactional(readOnly = true)
  public String getPdfPresignedUrl(Integer reportId, Integer userId) {
    WeeklyReport report =
        weeklyReportRepository
            .findById(reportId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "보고서를 찾을 수 없습니다."));
    if (!report.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }
    return s3Service.getPresignedDownloadUrl(report.getPdfKey());
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
