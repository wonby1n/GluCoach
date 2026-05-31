package com.ssafy.s309.domain.weekly_report.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportResponse;
import com.ssafy.s309.domain.weekly_report.service.WeeklyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-reports")
@RequiredArgsConstructor
@Tag(name = "리포트", description = "주간 혈당 보고서")
public class WeeklyReportController {

  private final WeeklyReportService weeklyReportService;

  @Operation(summary = "주간 보고서 목록 조회", description = "로그인한 사용자의 주간 보고서를 최신순으로 반환한다.")
  @GetMapping
  public ResponseEntity<List<WeeklyReportResponse>> list(
      @AuthenticationPrincipal CustomUserPrincipal principal) {
    return ResponseEntity.ok(weeklyReportService.findAllByUserId(principal.userId()));
  }

  @Operation(
      summary = "주간 보고서 PDF 다운로드",
      description = "S3 presigned URL(60분 유효)로 302 리다이렉트. 클라이언트는 자동으로 PDF를 다운로드한다.")
  @GetMapping("/{id}/pdf")
  public ResponseEntity<Void> getPdf(
      @PathVariable Integer id, @AuthenticationPrincipal CustomUserPrincipal principal) {
    String url = weeklyReportService.getPdfPresignedUrl(id, principal.userId());
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
  }
}
