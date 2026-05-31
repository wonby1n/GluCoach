package com.ssafy.s309.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiClientConfig {

  @Bean("aiRestClient")
  public RestClient aiRestClient(AiServiceProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  @Bean("aiReportRestClient")
  public RestClient aiReportRestClient(AiServiceProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.reportReadTimeoutMs()));

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  @Bean("aiPersonalizeRestClient")
  public RestClient aiPersonalizeRestClient(AiServiceProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.personalizeReadTimeoutMs()));

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  @Bean("aiExplainRestClient")
  public RestClient aiExplainRestClient(AiServiceProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(properties.explainReadTimeoutMs()));

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }
}
