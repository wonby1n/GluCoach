package com.ssafy.s309.domain.weekly_report.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyGlucoseProjection {
  LocalDate getDate();

  BigDecimal getAvg();

  BigDecimal getMin();

  BigDecimal getMax();
}
