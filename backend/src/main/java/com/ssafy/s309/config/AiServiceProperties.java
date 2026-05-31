package com.ssafy.s309.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-service")
public record AiServiceProperties(
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs,
    int reportReadTimeoutMs,
    int personalizeReadTimeoutMs) {

  public AiServiceProperties {
    if (connectTimeoutMs <= 0) connectTimeoutMs = 3_000;
    if (readTimeoutMs <= 0) readTimeoutMs = 5_000;
    if (reportReadTimeoutMs <= 0) reportReadTimeoutMs = 180_000; // LLM + PDF 생성 고려 3분
    if (personalizeReadTimeoutMs <= 0) personalizeReadTimeoutMs = 600_000; // CPU fine-tune 고려 10분
  }
}
