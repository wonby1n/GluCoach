package com.ssafy.s309.domain.weekly_report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WeeklyReportAiResponse {

  private String status;

  @JsonProperty("ai_summary")
  private String aiSummary;

  @JsonProperty("ai_suggest")
  private String aiSuggest;

  @JsonProperty("pdf_bytes")
  private String pdfBytes;

  private String error;

  public boolean isSuccess() {
    return "success".equals(status);
  }
}
