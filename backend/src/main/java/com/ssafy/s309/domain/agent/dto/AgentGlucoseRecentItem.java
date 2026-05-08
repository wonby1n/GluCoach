package com.ssafy.s309.domain.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 최근 혈당 + 직전 식사로부터 경과 분. 추천 agent의 모드 라우팅(식후 1h 이내, 위험 영역 등)에 사용.
 *
 * <p>latestMgDl/measuredAt이 모두 null이면 혈당 기록 없음. lastMealMinAgo가 null이면 최근 24h 식사 없음.
 */
public record AgentGlucoseRecentItem(
    BigDecimal latestMgDl, LocalDateTime measuredAt, Long lastMealMinAgo) {}
