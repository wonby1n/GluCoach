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
    val predictionId: Int,
    val curve: List<GlucoseCurvePoint>,
    val peakMgdl: Float,
    val peakMinute: Int,
    val confidence: Float,
)

@Serializable
data class GlucoseCurvePoint(
    val minuteOffset: Int,
    val glucoseMgdl: Float,
)
