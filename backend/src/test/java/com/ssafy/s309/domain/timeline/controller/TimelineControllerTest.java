package com.ssafy.s309.domain.timeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.timeline.dto.ExercisePin;
import com.ssafy.s309.domain.timeline.dto.GlucosePoint;
import com.ssafy.s309.domain.timeline.dto.MealPin;
import com.ssafy.s309.domain.timeline.dto.SleepPin;
import com.ssafy.s309.domain.timeline.dto.TimelineRange;
import com.ssafy.s309.domain.timeline.dto.TimelineResponse;
import com.ssafy.s309.domain.timeline.service.TimelineService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    controllers = TimelineController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class TimelineControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TimelineService timelineService;

  private static final Integer USER_ID = 1;

  private final RequestPostProcessor authedUser =
      authentication(
          new UsernamePasswordAuthenticationToken(
              new CustomUserPrincipal(USER_ID, "test@example.com"), null, List.of()));

  @Test
  void 타임라인_7일_조회_200_반환() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
    TimelineResponse response =
        new TimelineResponse(
            "7d",
            now.minusDays(7),
            now,
            List.of(
                new GlucosePoint(now.minusHours(1), new BigDecimal("105.00")),
                new GlucosePoint(now.minusMinutes(30), new BigDecimal("112.50"))),
            List.of(new MealPin(10, 100, now.minusHours(2), "s3-key-1")),
            List.of(
                new ExercisePin(
                    20, "WALKING", new BigDecimal("150.00"), now.minusHours(3), now.minusHours(2))),
            List.of(new SleepPin(30, now.minusHours(10), now.minusHours(2))));
    given(timelineService.getTimeline(eq(USER_ID), eq(TimelineRange.D7))).willReturn(response);

    mockMvc
        .perform(get("/api/timeline").param("range", "7d").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.range").value("7d"))
        .andExpect(jsonPath("$.glucosePoints.length()").value(2))
        .andExpect(jsonPath("$.glucosePoints[0].value").value(105.00))
        .andExpect(jsonPath("$.meals[0].id").value(10))
        .andExpect(jsonPath("$.exercises[0].exerciseType").value("WALKING"))
        .andExpect(jsonPath("$.sleeps[0].id").value(30));
  }

  @Test
  void range_생략_시_기본값_1d() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
    TimelineResponse response =
        new TimelineResponse(
            "1d", now.minusDays(1), now, List.of(), List.of(), List.of(), List.of());
    given(timelineService.getTimeline(eq(USER_ID), eq(TimelineRange.D1))).willReturn(response);

    mockMvc
        .perform(get("/api/timeline").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.range").value("1d"));
  }

  @Test
  void range_30d_조회_200_반환() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
    TimelineResponse response =
        new TimelineResponse(
            "30d", now.minusDays(30), now, List.of(), List.of(), List.of(), List.of());
    given(timelineService.getTimeline(eq(USER_ID), eq(TimelineRange.D30))).willReturn(response);

    mockMvc
        .perform(get("/api/timeline").param("range", "30d").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.range").value("30d"));
  }

  @Test
  void 지원하지_않는_range_400_반환() throws Exception {
    mockMvc
        .perform(get("/api/timeline").param("range", "99d").with(authedUser))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 빈_glucosePoints_빈_배열_반환() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 30, 12, 0);
    TimelineResponse response =
        new TimelineResponse(
            "7d", now.minusDays(7), now, List.of(), List.of(), List.of(), List.of());
    given(timelineService.getTimeline(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(get("/api/timeline").param("range", "7d").with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.glucosePoints").isArray())
        .andExpect(jsonPath("$.glucosePoints.length()").value(0));
  }
}
