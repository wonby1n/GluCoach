package com.ssafy.s309.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class FoodGradeInfo(
    val grade: String,
    val title: String,
    val glucoseRange: String,
    val description: String,
    val count: Int,
)

data class GradeFoodItem(
    val foodId: Int,
    val name: String,
    val frequency: Int,
    val lastEaten: String,
    val glucoseRise: Int,
    val trend: FoodTrend,
    val measureCount: Int,
)

enum class FoodTrend { UP, DOWN, STABLE }

@Serializable
data class FoodGradeResponse(
    @SerialName("foodId") val foodId: Int,
    @SerialName("foodName") val foodName: String,
    @SerialName("grade") val grade: String,
    @SerialName("avgSlope") val avgSlope: Double,
    @SerialName("mealCount") val mealCount: Int,
    @SerialName("lastEatenAt") val lastEatenAt: String,
    @SerialName("foodDisplayName") val foodDisplayName: String? = null,
)
