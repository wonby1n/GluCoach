plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.ssafy"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // API 문서
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // 데이터베이스
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // AWS S3
    implementation(platform("software.amazon.awssdk:bom:2.29.0"))
    implementation("software.amazon.awssdk:s3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // AWS S3
    implementation(platform("software.amazon.awssdk:bom:2.29.0"))
    implementation("software.amazon.awssdk:s3")

    // 개발 도구
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat("1.25.2")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
