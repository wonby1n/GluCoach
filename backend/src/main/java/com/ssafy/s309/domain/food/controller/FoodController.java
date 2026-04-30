package com.ssafy.s309.domain.food.controller;

import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.service.FoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Food", description = "음식 검색 API")
@RestController
@RequestMapping("/api/foods")
@Validated
@RequiredArgsConstructor
public class FoodController {

  private final FoodService foodService;

  @Operation(summary = "음식 검색", description = "DB 캐시 우선 조회, 캐시 미스 시 식품안전처 API 호출")
  @GetMapping("/search")
  public ResponseEntity<List<FoodSearchResult>> search(
      @RequestParam("q") @NotBlank @Size(min = 1, max = 50) String query) {
    return ResponseEntity.ok(foodService.search(query));
  }
}
