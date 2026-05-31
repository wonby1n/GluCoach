package com.ssafy.s309.domain.meal.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.meal.dto.MealRecordCreateRequest;
import com.ssafy.s309.domain.meal.dto.MealRecordCreateResponse;
import com.ssafy.s309.domain.meal.dto.MealRecordResponse;
import com.ssafy.s309.domain.meal.service.MealRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "식사", description = "식사 기록 API")
@RestController
@RequestMapping("/api/meals")
@RequiredArgsConstructor
public class MealRecordController {

  private final MealRecordService service;

  @Operation(summary = "식사 기록 생성", description = "식사를 기록하고 1시간 후 AI 트리거를 예약한다.")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<MealRecordCreateResponse> create(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestPart("request") @Valid MealRecordCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(principal.userId(), request, image));
  }

  @Operation(summary = "날짜별 식사 기록 조회", description = "지정한 날짜의 식사 기록 목록을 반환한다.")
  @GetMapping
  public ResponseEntity<List<MealRecordResponse>> getByDate(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(service.getByDate(principal.userId(), date));
  }

  @Operation(
      summary = "캘린더 dot 표시용 — 월별 식사 있는 날짜",
      description = "지정한 년/월에 식사 기록이 존재하는 day-of-month 목록을 반환한다.")
  @GetMapping("/calendar")
  public ResponseEntity<List<Integer>> getCalendarDays(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam int year,
      @RequestParam int month) {
    return ResponseEntity.ok(service.getDaysWithMealsInMonth(principal.userId(), year, month));
  }
}
