package com.ssafy.s309.domain.cgm.controller;

import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.cgm.dto.CgmRecordRequest;
import com.ssafy.s309.domain.cgm.dto.CgmRecordResponse;
import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.service.CgmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/glucose-records")
@RequiredArgsConstructor
@Tag(name = "CGM", description = "혈당 기록 수신 API")
public class CgmController {

  private static final Duration MAX_RANGE = Duration.ofDays(90);

  private final CgmService cgmService;

  @Operation(
      summary = "혈당 기록 수신",
      description =
          "CGM 기기 또는 시뮬레이터가 측정값을 전송. "
              + "value: 20-600 mg/dL. measuredAt: 현재 시각 기준 5분 초과 미래 거부. "
              + "(user_id, measured_at) 중복 시 409.")
  @PostMapping
  public ResponseEntity<CgmRecordResponse> record(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @Valid @RequestBody CgmRecordRequest request) {
    if (request.measuredAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "측정 시각이 허용 범위를 초과했습니다.");
    }
    GlucoseRecord saved =
        cgmService.save(principal.userId(), request.value(), request.measuredAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(CgmRecordResponse.from(saved));
  }

  @Operation(
      summary = "혈당 기록 기간 조회",
      description = "from/to 사이 측정값 시간 오름차순. from > to 또는 90일 초과 범위는 400.")
  @GetMapping
  public ResponseEntity<List<CgmRecordResponse>> list(
      @AuthenticationPrincipal CustomUserPrincipal principal,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    if (from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from은 to 이후일 수 없습니다.");
    }
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 범위는 최대 90일입니다.");
    }
    List<CgmRecordResponse> body =
        cgmService.findRange(principal.userId(), from, to).stream()
            .map(CgmRecordResponse::from)
            .toList();
    return ResponseEntity.ok(body);
  }

  /**
   * (user_id, measured_at) UNIQUE 위반 → 409. GlobalExceptionHandler의 generic 400 매핑보다 우선 적용되어 CGM
   * 재전송/시계 동기화 race를 명확히 표현.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateMeasurement(
      DataIntegrityViolationException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("message", "동일 시각의 측정값이 이미 존재합니다."));
  }
}
