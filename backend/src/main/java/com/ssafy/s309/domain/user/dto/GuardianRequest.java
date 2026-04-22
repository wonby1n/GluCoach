package com.ssafy.s309.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuardianRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 10~11자리 숫자여야 합니다") String phone,
    String relation,
    Boolean isPrimary) {}
