package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeeklyReportResponse(
    val id: Int,
    val weekStart: String,
    val avgGlucose: Double,
    val minGlucose: Double,
    val maxGlucose: Double,
    val glucoseSd: Double,
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double,
    val aiSummary: String = "",
    val aiSuggest: String = "",
    val createdAt: String = "",
    val foods: List<WeeklyFoodItem> = emptyList(),
    val dailyGlucose: List<DailyGlucoseItem> = emptyList(),
    val avgSteps: Double? = null,
    val avgSleepMinutes: Double? = null,
)

@Serializable
data class DailyGlucoseItem(
    val date: String,
    val avg: Double,
    val min: Double,
    val max: Double,
)

@Serializable
data class WeeklyFoodItem(
    val foodId: Int,
    val foodName: String,
    val type: String,
    val avgSlope: Double,
    val foodDisplayName: String? = null,
)
