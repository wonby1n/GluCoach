package com.ssafy.s309.domain.alert.dto;

import java.util.List;

public record AlertListResponse(
    List<AlertItemResponse> content, long unreadCount, int page, int size, long total) {}
