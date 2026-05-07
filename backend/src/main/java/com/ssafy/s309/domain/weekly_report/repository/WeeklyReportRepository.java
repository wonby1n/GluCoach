package com.ssafy.s309.domain.weekly_report.repository;

import com.ssafy.s309.domain.weekly_report.entity.WeeklyReport;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Integer> {

  List<WeeklyReport> findByUserIdOrderByWeekStartDesc(Integer userId);

  boolean existsByUserIdAndWeekStart(Integer userId, LocalDate weekStart);
}
