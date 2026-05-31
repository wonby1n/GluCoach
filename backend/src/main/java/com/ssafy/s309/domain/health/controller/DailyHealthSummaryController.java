package com.ssafy.s309.domain.health.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.health.dto.DailyHealthSummaryResponse;
import com.ssafy.s309.domain.health.dto.DailyHealthSummaryUpsertRequest;
import com.ssafy.s309.domain.health.service.DailyHealthSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health/daily-summary")
@RequiredArgsConstructor
@Tag(name = "건강 데이터", description = "삼성 헬스 일별 요약 (대시보드 + Agent)")
public class DailyHealthSummaryController {

  private final DailyHealthSummaryService service;

  @Operation(
      summary = "일별 헬스 요약 upsert",
      description =
          "삼성 헬스 SDK에서 받은 누적값을 (user_id, date) 기준으로 upsert. "
              + "1분마다 호출 권장. null 필드는 기존값 유지(부분 갱신).")
  @PostMapping
  public ResponseEntity<DailyHealthSummaryResponse> upsert(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody DailyHealthSummaryUpsertRequest request) {
    return ResponseEntity.ok(service.upsert(principal.userId(), request));
  }

  @Operation(
      summary = "일별 헬스 요약 기간 조회",
      description = "from~to (포함) 일자 범위의 요약 리스트. 대시보드 카드/주간 차트/Agent 조회용.")
  @GetMapping
  public ResponseEntity<List<DailyHealthSummaryResponse>> findRange(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Parameter(description = "시작일 (YYYY-MM-DD)", example = "2026-04-26")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "종료일 (YYYY-MM-DD)", example = "2026-05-02")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return ResponseEntity.ok(service.findRange(principal.userId(), from, to));
  }
}
