package com.ssafy.s309.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatReplyRequest(@NotNull Long parentId, @NotBlank @Size(max = 50) String optionId) {}
