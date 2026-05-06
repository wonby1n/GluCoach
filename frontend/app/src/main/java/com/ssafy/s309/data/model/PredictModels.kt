package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GlucoseCompareRequest(
    val foodA: FoodCompareItem,
    val foodB: FoodCompareItem,
)

@Serializable
data class FoodCompareItem(
    val foodId: Long,
    val foodName: String,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    val kcal: Int,
    val sugarG: Int,
    val giScore: Int? = null,
)

@Serializable
data class GlucoseCompareResponse(
    val foodA: GlucosePrediction,
    val foodB: GlucosePrediction,
)

@Serializable
data class GlucosePrediction(
    val predictionId: Int = 0,
    val curve: List<GlucoseCurvePoint> = emptyList(),
    val peakMgdl: Float = 0f,
    val peakMinute: Int = 0,
    val confidence: Float = 0f,
)

@Serializable
data class GlucoseCurvePoint(
    val minuteOffset: Int,
    val glucoseMgdl: Float,
)
