package com.ssafy.s309;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    // TODO: 다음 작업 - 도메인 패키지 구조 생성 (domain/, repository/, service/, dto/)
    // TODO: Spring Security 설정 클래스 추가 (SecurityConfig.java)
    // TODO: JWT 인증 구현
}
