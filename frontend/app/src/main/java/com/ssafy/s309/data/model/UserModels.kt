package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val userId: Int = 0,
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val phone: String? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val diabetesType: String? = null,
    val isMedicated: Boolean? = null,
    val targetLow: Double? = null,
    val targetHigh: Double? = null,
    val weekStartDay: Int? = null,
    // 백엔드 SettingsResponse에 없는 클라이언트 전용 필드 (null로 수신됨)
    val alertLow: Int? = null,
    val alertHigh: Int? = null,
    val nightWatch: Boolean? = null,
    val characterType: String? = null,
)

@Serializable
data class UserSettingsUpdateRequest(
    val name: String? = null,
    val age: Int? = null,
    val phone: String? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val diabetesType: String? = null,
    val isMedicated: Boolean? = null,
    val targetLow: Double? = null,
    val targetHigh: Double? = null,
    // 백엔드 SettingsUpdateRequest에 없는 필드 (서버가 무시함)
    val alertLow: Int? = null,
    val alertHigh: Int? = null,
    val nightWatch: Boolean? = null,
    val characterType: String? = null,
)

/** 백엔드 GET /api/user/search 응답 */
@Serializable
data class UserSearchResponse(
    val userId: Int,
    val name: String,
)

/** 백엔드 GET /api/user/guardians 응답 항목 (GuardianResponse 매핑) */
@Serializable
data class GuardianItem(
    val id: Int,
    val wardId: Int,
    val guardianId: Int,
    val relation: String? = null,
    val priority: Int = 0,
    /** 서버 응답에는 없음 — 보호자 추가 시 검색 결과에서 채워넣는 표시용 필드 */
    val name: String = "",
)

/** 백엔드 POST /api/user/guardians 요청 (GuardianRequest 매핑) */
@Serializable
data class GuardianCreateRequest(
    val guardianId: Int,
    val relation: String? = null,
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val deviceType: String? = "android",
)
