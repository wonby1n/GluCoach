package com.ssafy.s309.domain.food.controller;

import com.ssafy.s309.domain.food.dto.FoodIngestResult;
import com.ssafy.s309.domain.food.service.FoodBulkIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "식사", description = "음식 데이터 관리 API (관리자 전용)")
@Slf4j
@RestController
@RequestMapping("/api/admin/foods")
@Validated
@RequiredArgsConstructor
public class FoodAdminController {

  private final FoodBulkIngestService foodBulkIngestService;

  @Value("${admin.api-key:}")
  private String adminApiKey;

  @Operation(
      summary = "음식 데이터 벌크 적재",
      description =
          "공공데이터포털 식품영양성분 CSV를 foods 테이블에 적재한다. "
              + "food_api_id 기준 UPSERT, is_customized=true row는 보존.")
  @PostMapping("/ingest")
  public ResponseEntity<FoodIngestResult> ingest(
      @RequestHeader("X-Admin-Key") String adminKey, @RequestBody IngestRequest request)
      throws IOException {
    if (adminApiKey.isBlank() || !adminApiKey.equals(adminKey)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    Path csvPath = Paths.get(request.path());
    log.info("[FoodIngest] CSV 적재 시작 path={}", csvPath);
    try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
      FoodIngestResult result = foodBulkIngestService.ingest(reader);
      return ResponseEntity.ok(result);
    }
  }

  public record IngestRequest(@NotBlank String path) {}
}
