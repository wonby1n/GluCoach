package com.ssafy.s309.domain.auth.principal;

import java.util.UUID;

public record CustomUserPrincipal(UUID userId, String email) {}
