package com.ssafy.s309.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";
  private static final String AGENT_API_KEY_SCHEME = "agentApiKey";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("GlucoCoach API").version("v1"))
        // 두 스키마 모두 글로벌로 등록 — Swagger UI Authorize 패널에서 둘 다 입력 가능.
        // 실제 어떤 스키마를 쓰는지는 컨트롤러별 @SecurityRequirement로 분기 가능하나,
        // 시연 단계에선 Authorize 한 번 후 모든 endpoint 자동 적용으로 충분.
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .addSecurityItem(new SecurityRequirement().addList(AGENT_API_KEY_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(
                    AGENT_API_KEY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Agent-Api-Key")
                        .description("Agent API 전용 인증 헤더. 값: agent.api-key 설정값")));
  }
}
