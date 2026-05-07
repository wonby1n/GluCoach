package com.ssafy.s309.domain.weekly_report.dto.projection;

import java.math.BigDecimal;

public interface WeeklyGlucoseStatsProjection {
  BigDecimal getAvgGlucose();

  BigDecimal getMinGlucose();

  BigDecimal getMaxGlucose();

  BigDecimal getGlucoseSd();

  BigDecimal getTimeAboveRange();

  BigDecimal getTimeBelowRange();
}
