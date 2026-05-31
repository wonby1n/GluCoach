package com.ssafy.s309.widget

import kotlinx.serialization.Serializable

/**
 * 위젯에 표시할 사전 계산된 상태. Worker 가 API 호출 + 통계 계산 후 여기 저장하면
 * Glance 가 readraw 한다.
 */
@Serializable
data class GlucoseWidgetState(
    val currentValue: Int? = null,
    val trendDelta: Int? = null,
    val updatedAtText: String = "—",
    val avg: Int? = null,
    val max: Int? = null,
    val min: Int? = null,
    val inRangePct: Int? = null,
    val targetLow: Int = 70,
    val targetHigh: Int = 140,
    val chartPngPath: String? = null,
    val isLoggedIn: Boolean = true,
    val errorMessage: String? = null,
)
