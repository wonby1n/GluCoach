package com.ssafy.s309.domain.food.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.food.dto.FoodSearchResult;
import com.ssafy.s309.domain.food.exception.FoodApiException;
import com.ssafy.s309.domain.food.service.FoodService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = FoodController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class FoodControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FoodService foodService;

  @Test
  void 검색_200_반환() throws Exception {
    FoodSearchResult result =
        new FoodSearchResult(
            1L,
            "밥, 흰쌀",
            new BigDecimal("143.00"),
            new BigDecimal("31.50"),
            new BigDecimal("0.10"),
            new BigDecimal("2.60"),
            new BigDecimal("0.30"));
    given(foodService.search("밥")).willReturn(List.of(result));

    mockMvc
        .perform(get("/api/foods/search").param("q", "밥"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("밥, 흰쌀"))
        .andExpect(jsonPath("$[0].kcal").value(143.0));
  }

  @Test
  void 검색_빈_쿼리_400() throws Exception {
    mockMvc.perform(get("/api/foods/search").param("q", "")).andExpect(status().isBadRequest());
  }

  @Test
  void 검색_쿼리_누락_400() throws Exception {
    mockMvc.perform(get("/api/foods/search")).andExpect(status().isBadRequest());
  }

  @Test
  void API_장애_시_503_반환() throws Exception {
    given(foodService.search(ArgumentMatchers.anyString()))
        .willThrow(new FoodApiException("식품안전처 API 연결 실패"));

    mockMvc
        .perform(get("/api/foods/search").param("q", "밥"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("식품안전처 API 연결 실패"));
  }
}
