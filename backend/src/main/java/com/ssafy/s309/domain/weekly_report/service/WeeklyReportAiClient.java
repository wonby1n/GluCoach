package com.ssafy.s309.domain.weekly_report.service;

import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiRequest;
import com.ssafy.s309.domain.weekly_report.dto.WeeklyReportAiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class WeeklyReportAiClient {

  private final RestClient aiReportRestClient;

  public WeeklyReportAiClient(@Qualifier("aiReportRestClient") RestClient aiReportRestClient) {
    this.aiReportRestClient = aiReportRestClient;
  }

  /**
   * AI 서비스에 주간 보고서 생성을 요청한다.
   *
   * @throws RuntimeException AI 서비스 호출 실패 또는 오류 응답 시
   */
  public WeeklyReportAiResponse generate(WeeklyReportAiRequest request) {
    log.info("AI 보고서 생성 요청: userId={} week={}", request.getUserId(), request.getWeekStart());
    try {
      WeeklyReportAiResponse response =
          aiReportRestClient
              .post()
              .uri("/report/weekly")
              .body(request)
              .retrieve()
              .body(WeeklyReportAiResponse.class);

      if (response == null) {
        throw new RuntimeException("AI 서비스 응답이 null");
      }
      if (!response.isSuccess()) {
        throw new RuntimeException("AI 보고서 생성 실패: " + response.getError());
      }

      log.info("AI 보고서 생성 완료: userId={}", request.getUserId());
      return response;

    } catch (RestClientException e) {
      log.error("AI 서비스 호출 실패: userId={} cause={}", request.getUserId(), e.getMessage());
      throw new RuntimeException("AI 서비스 호출 실패: " + e.getMessage(), e);
    }
  }
}
