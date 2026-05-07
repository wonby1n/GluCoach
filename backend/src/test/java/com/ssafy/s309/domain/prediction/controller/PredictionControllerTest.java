package com.ssafy.s309.domain.prediction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.s309.config.SecurityConfig;
import com.ssafy.s309.config.TestSecurityConfig;
import com.ssafy.s309.domain.auth.principal.CustomUserPrincipal;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse;
import com.ssafy.s309.domain.prediction.dto.FromImagePredictResponse.Status;
import com.ssafy.s309.domain.prediction.service.FromImagePredictionService;
import com.ssafy.s309.domain.prediction.service.PredictionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    controllers = PredictionController.class,
    excludeFilters = {
      @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
      @Filter(type = FilterType.REGEX, pattern = "com\\.ssafy\\.s309\\.domain\\.auth\\..*")
    })
@Import(TestSecurityConfig.class)
@SuppressWarnings("NonAsciiCharacters")
class PredictionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PredictionService predictionService;
  @MockitoBean private FromImagePredictionService fromImagePredictionService;

  private static final Integer USER_ID = 1;

  private final RequestPostProcessor authedUser =
      authentication(
          new UsernamePasswordAuthenticationToken(
              new CustomUserPrincipal(USER_ID, "test@example.com"), null, List.of()));

  private static MockMultipartFile imagePart() {
    return new MockMultipartFile("image", "test.jpg", "image/jpeg", new byte[] {1, 2, 3});
  }

  @Test
  void from_image_200_반환() throws Exception {
    FromImagePredictResponse response =
        new FromImagePredictResponse(Status.OK, List.of(), 10, "비빔밥", null, false);
    given(fromImagePredictionService.predict(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(multipart("/api/predict/glucose/from-image").file(imagePart()).with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.foodId").value(10))
        .andExpect(jsonPath("$.foodName").value("비빔밥"))
        .andExpect(jsonPath("$.requireConfirmation").value(false));
  }

  @Test
  void from_image_LOW_CONFIDENCE_응답() throws Exception {
    FromImagePredictResponse response =
        new FromImagePredictResponse(Status.LOW_CONFIDENCE, List.of(), null, null, null, true);
    given(fromImagePredictionService.predict(eq(USER_ID), any())).willReturn(response);

    mockMvc
        .perform(multipart("/api/predict/glucose/from-image").file(imagePart()).with(authedUser))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LOW_CONFIDENCE"))
        .andExpect(jsonPath("$.requireConfirmation").value(true))
        .andExpect(jsonPath("$.foodId").doesNotExist());
  }

  @Test
  void from_image_빈_파일_400() throws Exception {
    MockMultipartFile empty =
        new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);
    mockMvc
        .perform(multipart("/api/predict/glucose/from-image").file(empty).with(authedUser))
        .andExpect(status().isBadRequest());
  }

  @Test
  void from_image_이미지_아닌_컨텐츠타입_400() throws Exception {
    MockMultipartFile notImage =
        new MockMultipartFile("image", "file.txt", "text/plain", new byte[] {1, 2, 3});
    mockMvc
        .perform(multipart("/api/predict/glucose/from-image").file(notImage).with(authedUser))
        .andExpect(status().isBadRequest());
  }
}
