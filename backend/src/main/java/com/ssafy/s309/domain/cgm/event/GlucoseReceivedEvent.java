package com.ssafy.s309.domain.cgm.event;

import java.math.BigDecimal;

public record GlucoseReceivedEvent(Integer userId, BigDecimal value, Long recordId) {}
