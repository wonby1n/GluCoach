package com.ssafy.s309.domain.cgm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.cgm.dto.CgmRecordRequest;
import com.ssafy.s309.domain.cgm.entity.GlucoseRecord;
import com.ssafy.s309.domain.cgm.service.CgmService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    controllers = CgmController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class CgmControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private CgmService cgmService;

  private static final Integer USER_ID = 1;

  private final RequestPostProcessor authedUser =
      authentication(
          new UsernamePasswordAuthenticationToken(
              new CustomUserPrincipal(USER_ID, "test@example.com"), null, List.of()));

  private GlucoseRecord record(Long id, BigDecimal value, LocalDateTime measuredAt) {
    return GlucoseRecord.builder().userId(USER_ID).value(value).measuredAt(measuredAt).build();
  }

  @Test
  void POST_정상_저장_201_반환() throws Exception {
    LocalDateTime measuredAt = LocalDateTime.of(2026, 5, 4, 12, 0);
    BigDecimal value = new BigDecimal("142");
    given(cgmService.save(eq(USER_ID), eq(value), eq(measuredAt)))
        .willReturn(record(10L, value, measuredAt));

    CgmRecordRequest req = new CgmRecordRequest(value, measuredAt);
    mockMvc
        .perform(
            post("/api/glucose-records")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.value").value(142))
        .andExpect(jsonPath("$.measuredAt").exists());
  }

  @Test
  void POST_value_범위_초과_400() throws Exception {
    CgmRecordRequest req =
        new CgmRecordRequest(new BigDecimal("700"), LocalDateTime.of(2026, 5, 4, 12, 0));
    mockMvc
        .perform(
            post("/api/glucose-records")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void POST_value_하한_미만_400() throws Exception {
    CgmRecordRequest req =
        new CgmRecordRequest(new BigDecimal("10"), LocalDateTime.of(2026, 5, 4, 12, 0));
    mockMvc
        .perform(
            post("/api/glucose-records")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void POST_미래_시각_5분_초과_400() throws Exception {
    LocalDateTime future = LocalDateTime.now().plusHours(1);
    CgmRecordRequest req = new CgmRecordRequest(new BigDecimal("120"), future);
    mockMvc
        .perform(
            post("/api/glucose-records")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void POST_중복_measured_at_409() throws Exception {
    LocalDateTime measuredAt = LocalDateTime.of(2026, 5, 4, 12, 0);
    given(cgmService.save(any(), any(), any()))
        .willThrow(new DataIntegrityViolationException("unique violation"));

    CgmRecordRequest req = new CgmRecordRequest(new BigDecimal("120"), measuredAt);
    mockMvc
        .perform(
            post("/api/glucose-records")
                .with(authedUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("동일 시각의 측정값이 이미 존재합니다."));
  }

  @Test
  void GET_기간_조회_200_시간순_반환() throws Exception {
    LocalDateTime from = LocalDateTime.of(2026, 5, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 5, 4, 23, 59);
    given(cgmService.findRange(eq(USER_ID), eq(from), eq(to)))
        .willReturn(
            List.of(
                record(1L, new BigDecimal("110"), from.plusHours(1)),
                record(2L, new BigDecimal("145"), from.plusHours(2))));

    mockMvc
        .perform(
            get("/api/glucose-records")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].value").value(110))
        .andExpect(jsonPath("$[1].value").value(145));
  }

  @Test
  void GET_from_to_역전_400() throws Exception {
    LocalDateTime from = LocalDateTime.of(2026, 5, 4, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 5, 1, 0, 0);

    mockMvc
        .perform(
            get("/api/glucose-records")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(authedUser))
        .andExpect(status().isBadRequest());
  }

  @Test
  void GET_범위_90일_초과_400() throws Exception {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = from.plusDays(91);

    mockMvc
        .perform(
            get("/api/glucose-records")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(authedUser))
        .andExpect(status().isBadRequest());
  }
}
