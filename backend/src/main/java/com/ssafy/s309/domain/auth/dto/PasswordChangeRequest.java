package com.ssafy.s309.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
    @NotBlank(message = "현재 비밀번호는 필수입니다") String currentPassword,
    @NotBlank(message = "새 비밀번호는 필수입니다") @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다")
        String newPassword) {}
