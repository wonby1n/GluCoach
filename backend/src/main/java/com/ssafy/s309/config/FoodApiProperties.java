package com.ssafy.s309.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "food-api")
public record FoodApiProperties(
    String baseUrl, String serviceKey, int connectTimeoutMs, int readTimeoutMs) {

  public FoodApiProperties {
    if (connectTimeoutMs <= 0) connectTimeoutMs = 3000;
    if (readTimeoutMs <= 0) readTimeoutMs = 5000;
  }
}
