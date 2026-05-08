package com.ssafy.s309.config;

import com.ssafy.s309.domain.auth.jwt.JwtAccessDeniedHandler;
import com.ssafy.s309.domain.auth.jwt.JwtAuthenticationEntryPoint;
import com.ssafy.s309.domain.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/health",
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/email/check",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/api-docs/**",
                        "/v3/api-docs/**",
                        "/webjars/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
