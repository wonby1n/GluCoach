package com.ssafy.s309.domain.food.service;

import com.ssafy.s309.domain.food.dto.FoodIngestResult;
import com.ssafy.s309.domain.food.entity.Food;
import com.ssafy.s309.domain.food.repository.FoodRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodBulkIngestService {

  private static final BigDecimal DEFAULT_SERVING_SIZE = new BigDecimal("100");

  private final FoodRepository foodRepository;

  @Transactional
  public FoodIngestResult ingest(Reader csvSource) throws IOException {
    long startMs = System.currentTimeMillis();
    LocalDateTime now = LocalDateTime.now();
    int totalRows = 0;
    int inserted = 0;
    int updated = 0;
    int skippedInvalid = 0;

    try (BufferedReader reader = new BufferedReader(csvSource)) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new IllegalArgumentException("CSV가 비어 있음 — 헤더 라인이 없습니다");
      }
      Map<String, Integer> headerIdx = parseHeader(headerLine);

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        totalRows++;
        try {
          List<String> fields = parseLine(line);
          String apiId = field(fields, headerIdx, "FOOD_CD");
          String name = field(fields, headerIdx, "FOOD_NM_KR");
          if (apiId == null || name == null) {
            skippedInvalid++;
            continue;
          }

          Optional<Food> existingOpt = foodRepository.findByFoodApiId(apiId);
          if (existingOpt.isPresent()) {
            Food existing = existingOpt.get();
            existing.refresh(
                field(fields, headerIdx, "FOOD_CAT1_NM"),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM1")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM6")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM7")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM3")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM4")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM8")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM24")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM25")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM23")),
                parseBigDecimal(field(fields, headerIdx, "AMT_NUM13")));
            updated++;
          } else {
            BigDecimal servingSize = parseServingSize(field(fields, headerIdx, "SERVING_SIZE"));
            if (servingSize == null) {
              servingSize = DEFAULT_SERVING_SIZE;
            }
            foodRepository.save(
                Food.builder()
                    .foodApiId(apiId)
                    .name(name)
                    .category(field(fields, headerIdx, "FOOD_CAT1_NM"))
                    .kcal(parseBigDecimal(field(fields, headerIdx, "AMT_NUM1")))
                    .carbsG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM6")))
                    .sugarG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM7")))
                    .proteinG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM3")))
                    .fatG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM4")))
                    .fiberG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM8")))
                    .saturatedFatG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM24")))
                    .transFatG(parseBigDecimal(field(fields, headerIdx, "AMT_NUM25")))
                    .cholesterolMg(parseBigDecimal(field(fields, headerIdx, "AMT_NUM23")))
                    .sodiumMg(parseBigDecimal(field(fields, headerIdx, "AMT_NUM13")))
                    .servingSize(servingSize)
                    .isCustomized(false)
                    .searchCount(0)
                    .cachedAt(now)
                    .build());
            inserted++;
          }
        } catch (RuntimeException e) {
          log.warn("[FoodIngest] row 처리 실패 row={} reason={}", totalRows, e.getMessage());
          skippedInvalid++;
        }
      }
    }

    long elapsedMs = System.currentTimeMillis() - startMs;
    log.info(
        "[FoodIngest] 완료 totalRows={} inserted={} updated={} skippedInvalid={} elapsedMs={}",
        totalRows,
        inserted,
        updated,
        skippedInvalid,
        elapsedMs);
    return new FoodIngestResult(totalRows, inserted, updated, skippedInvalid, elapsedMs);
  }

  private static Map<String, Integer> parseHeader(String line) {
    List<String> headers = parseLine(line);
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      map.put(headers.get(i).trim(), i);
    }
    return map;
  }

  private static List<String> parseLine(String line) {
    List<String> result = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuote = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuote) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            inQuote = false;
          }
        } else {
          field.append(c);
        }
      } else {
        if (c == ',') {
          result.add(field.toString());
          field.setLength(0);
        } else if (c == '"') {
          inQuote = true;
        } else {
          field.append(c);
        }
      }
    }
    result.add(field.toString());
    return result;
  }

  private static String field(List<String> fields, Map<String, Integer> headerIdx, String name) {
    Integer idx = headerIdx.get(name);
    if (idx == null || idx >= fields.size()) {
      return null;
    }
    String v = fields.get(idx).trim();
    return v.isEmpty() ? null : v;
  }

  private static BigDecimal parseBigDecimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static BigDecimal parseServingSize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String numeric = raw.replaceAll("[^0-9.]", "");
    if (numeric.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(numeric);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
