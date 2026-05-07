package com.ssafy.s309.domain.weekly_report.dto.projection;

import java.math.BigDecimal;

public interface WeeklyFoodItemProjection {
  String getFoodName();

  BigDecimal getAvgSlope();
}
