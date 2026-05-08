package com.ssafy.s309.domain.chat.dto;

import java.util.List;

public record ChatMessageListResponse(
    List<ChatMessageItem> content, int page, int size, long total, long unreadCount) {}
