package com.ssafy.s309.domain.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Agent #7 send_notification 요청. JSON 키 alertType은 외부 호환을 위해 유지 (Agent Python tools.py 의존). 내부 매핑
 * messageType.
 *
 * <p>options/displayTrace/payload는 모두 선택. options는 0~10개 (구 정확히 3개 강제 완화). 선택지 없는 단순 알림 메시지도 허용.
 */
public record AgentNotificationCreateRequest(
    @NotNull Integer userId,
    @NotBlank @Size(max = 50) String alertType,
    @NotBlank @Size(max = 500) String message,
    @Size(max = 10) List<Map<String, String>> options,
    Map<String, Object> displayTrace,
    Map<String, Object> payload) {}
