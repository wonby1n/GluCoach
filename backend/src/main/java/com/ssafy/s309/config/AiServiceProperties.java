package com.ssafy.s309.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-service")
public record AiServiceProperties(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {

  public AiServiceProperties {
    if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
    if (readTimeoutMs <= 0) readTimeoutMs = 5000;
  }
}
