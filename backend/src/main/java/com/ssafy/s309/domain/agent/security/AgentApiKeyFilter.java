package com.ssafy.s309.domain.agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/agent/** 전용 API 키 인증 필터.
 *
 * <p>Agent는 백엔드 서비스(M2M)이므로 사용자 JWT가 아닌 고정 API 키로 인증한다. 헤더 {@code X-Agent-Api-Key}가 설정값과 일치하면
 * ROLE_AGENT 권한으로 SecurityContext를 채운다.
 */
@Component
public class AgentApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Agent-Api-Key";

  private final String expectedKey;

  public AgentApiKeyFilter(@Value("${agent.api-key}") String expectedKey) {
    this.expectedKey = expectedKey;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String provided = request.getHeader(HEADER_NAME);
    if (provided != null && provided.equals(expectedKey)) {
      var auth =
          new UsernamePasswordAuthenticationToken(
              "agent", null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
    chain.doFilter(request, response);
  }
}
