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
    val targetLow: Int? = null,
    val targetHigh: Int? = null,
    val alertLow: Int? = null,
    val alertHigh: Int? = null,
    val nightWatch: Boolean? = null,
    val characterType: String? = null,
    val weekStartDay: Int? = null,
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
data class UserProfile(
    val email: String = "",
    val name: String = "",
    val age: Int? = null,
    val phone: String = "",
)

@Serializable
data class UserProfileUpdateRequest(
    val name: String? = null,
    val age: Int? = null,
    val phone: String? = null,
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val deviceType: String? = "android",
)
