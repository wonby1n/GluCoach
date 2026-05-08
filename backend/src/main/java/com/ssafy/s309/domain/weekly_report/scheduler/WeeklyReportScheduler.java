package com.ssafy.s309.domain.weekly_report.scheduler;

import com.ssafy.s309.domain.user.entity.User;
import com.ssafy.s309.domain.user.repository.UserRepository;
import com.ssafy.s309.domain.weekly_report.service.WeeklyReportService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final UserRepository userRepository;
  private final WeeklyReportService weeklyReportService;

  /**
   * 매일 오전 9시(KST) 실행. 오늘이 week_start_day인 유저들의 전 주(7일) 보고서를 생성한다.
   *
   * <p>예) week_start_day=1(월요일)인 유저 → 매주 월요일 오전 9시에 지난 주(월~일) 보고서 생성
   */
  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void generateWeeklyReports() {
    LocalDate today = LocalDate.now(KST);
    short todayDow = (short) today.getDayOfWeek().getValue(); // 1=MON ... 7=SUN

    List<User> targets = userRepository.findByWeekStartDayAndDeletedAtIsNull(todayDow);
    if (targets.isEmpty()) {
      return;
    }

    LocalDate weekStart = today.minusDays(7); // 전 주 시작일
    log.info("주간 보고서 스케줄러 실행: date={} dow={} 대상유저={}명", today, todayDow, targets.size());

    for (User user : targets) {
      try {
        weeklyReportService.generateForUser(user, weekStart);
      } catch (Exception e) {
        log.warn("주간 보고서 생성 실패 (스킵): userId={} cause={}", user.getId(), e.getMessage());
      }
    }
  }
}
