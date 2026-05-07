package com.ssafy.s309.domain.weekly_report.repository;

import com.ssafy.s309.domain.weekly_report.entity.WeeklyFood;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyFoodRepository extends JpaRepository<WeeklyFood, Integer> {

  List<WeeklyFood> findByReportId(Integer reportId);
}
