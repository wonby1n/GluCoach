package com.ssafy.s309.data.model

import androidx.annotation.DrawableRes

data class FoodGradeInfo(
    val grade: String,
    val title: String,
    val glucoseRange: String,
    val description: String,
    val count: Int,
)

data class GradeFoodItem(
    val name: String,
    @param:DrawableRes val imageResId: Int,
    val frequency: Int,
    val lastEaten: String,
    val glucoseRise: Int,
    val trend: FoodTrend,
    val measureCount: Int,
)

enum class FoodTrend { UP, DOWN, STABLE }
