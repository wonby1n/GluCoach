package com.ssafy.s309.domain.weekly_report.repository;

import com.ssafy.s309.domain.weekly_report.entity.WeeklyFood;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyFoodRepository extends JpaRepository<WeeklyFood, Integer> {

  List<WeeklyFood> findByReportId(Integer reportId);

  @Query("SELECT wf FROM WeeklyFood wf JOIN FETCH wf.food WHERE wf.reportId IN :reportIds")
  List<WeeklyFood> findByReportIdInWithFood(@Param("reportIds") List<Integer> reportIds);
}
