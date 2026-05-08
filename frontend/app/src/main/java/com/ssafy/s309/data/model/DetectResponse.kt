package com.ssafy.s309.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DetectResponse(
    val count: Int,
    val detections: List<DetectionResult>,
)

@Serializable
data class DetectionResult(
    @SerialName("name_ko") val nameKo: String,
    @SerialName("name_en") val nameEn: String,
    val confidence: Float,
    val bbox: BBox,
)

@Serializable
data class BBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)
