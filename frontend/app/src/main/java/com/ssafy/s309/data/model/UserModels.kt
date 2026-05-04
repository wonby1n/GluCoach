package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val userId: String,
    val height: Float?,
    val weight: Float?,
    val diabetesType: String,
    val isMedicated: Boolean,
    val targetLow: Int,
    val targetHigh: Int,
    val alertLow: Int,
    val alertHigh: Int,
    val nightWatch: Boolean,
    val characterType: String,
)

@Serializable
data class UserSettingsUpdateRequest(
    val height: Float? = null,
    val weight: Float? = null,
    val diabetesType: String? = null,
    val isMedicated: Boolean? = null,
    val targetLow: Int? = null,
    val targetHigh: Int? = null,
    val alertLow: Int? = null,
    val alertHigh: Int? = null,
    val nightWatch: Boolean? = null,
    val characterType: String? = null,
)

@Serializable
data class GuardianItem(
    val guardianId: String,
    val name: String,
    val phone: String,
    val relation: String? = null,
    val isPrimary: Boolean,
    val priority: Int,
)

@Serializable
data class GuardianCreateRequest(
    val name: String,
    val phone: String,
    val relation: String? = null,
    val isPrimary: Boolean = false,
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val deviceType: String? = "android",
)
