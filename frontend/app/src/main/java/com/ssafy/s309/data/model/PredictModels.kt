package com.ssafy.s309.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 사진 통합 식전 예측 응답 (BE FromImagePredictResponse 미러).
 *
 * status:
 *  - OK                — 인식·매칭·예측 모두 성공. foodId/foodName/prediction 채워짐
 *  - LOW_CONFIDENCE    — 신뢰도 미달 (top-1 conf < 0.6 또는 nameKo 누락). 사용자 직접 확인 필요
 *  - PENDING_NUTRITION — 인식했으나 영양정보 매칭 못함. foodId/foodName 채워지지만 prediction null
 */
@Serializable
data class FromImagePredictResponse(
    val status: String,
    val detected: List<FoodDetection> = emptyList(),
    val foodId: Int? = null,
    val foodName: String? = null,
    val prediction: GlucosePrediction? = null,
    val requireConfirmation: Boolean = false,
)

@Serializable
data class FoodDetection(
    @SerialName("name_ko") val nameKo: String,
    // BE record 가 nullable 로 받음 — AI 가 #1170 이후 보내지 않으므로 사실상 항상 null
    @SerialName("name_en") val nameEn: String? = null,
    val confidence: Float,
)

@Serializable
data class PredictRequest(
    val foodId: Int?,
    val foodName: String,
    val carbsG: Double,
    val proteinG: Double,
    val fatG: Double,
    val fiberG: Double? = null,
    val kcal: Double,
    val sugarG: Double? = null,
    val giScore: Int? = null,
)

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

@Serializable
data class CompareExplainRequest(
    val foodAName: String,
    val foodBName: String,
    val foodAPeakMgdl: Float,
    val foodAPeakMinute: Int,
    val foodASlope: Float,
    val foodBPeakMgdl: Float,
    val foodBPeakMinute: Int,
    val foodBSlope: Float,
)

@Serializable
data class CompareExplainResponse(
    val message: String,
)
