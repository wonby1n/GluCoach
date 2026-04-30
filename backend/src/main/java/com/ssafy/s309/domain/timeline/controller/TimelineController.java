package com.ssafy.s309.domain.timeline.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.timeline.dto.TimelineRange;
import com.ssafy.s309.domain.timeline.dto.TimelineResponse;
import com.ssafy.s309.domain.timeline.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
@Tag(name = "Timeline", description = "타임라인 통합 조회 API (혈당 + 식사/운동/수면 이벤트)")
public class TimelineController {

  private final TimelineService timelineService;

  @Operation(
      summary = "타임라인 통합 조회",
      description =
          "JWT 인증된 본인의 혈당 시계열 + 식사/운동/수면 이벤트를 한 번에 반환. "
              + "range 토큰: 1d / 7d / 30d (기본 1d). 4쿼리 병렬 실행.")
  @GetMapping
  public ResponseEntity<TimelineResponse> getTimeline(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Parameter(description = "조회 범위 토큰: 1d / 7d / 30d", example = "7d")
          @RequestParam(defaultValue = "1d")
          String range) {
    TimelineRange parsed = TimelineRange.parse(range);
    return ResponseEntity.ok(timelineService.getTimeline(principal.userId(), parsed));
  }
}
