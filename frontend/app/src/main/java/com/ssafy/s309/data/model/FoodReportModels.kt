package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

data class FoodGradeInfo(
    val grade: String,
    val title: String,
    val glucoseRange: String,
    val description: String,
    val count: Int,
)

data class GradeFoodItem(
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
    val foodId: Int,
    val foodName: String,
    val grade: String,
    val avgSlope: Double,
    val mealCount: Int,
    val lastEatenAt: String,
)
