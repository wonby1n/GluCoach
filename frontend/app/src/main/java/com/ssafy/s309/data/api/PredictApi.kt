package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.CompareExplainRequest
import com.ssafy.s309.data.model.CompareExplainResponse
import com.ssafy.s309.data.model.FromImagePredictResponse
import com.ssafy.s309.data.model.GlucoseCompareRequest
import com.ssafy.s309.data.model.GlucoseCompareResponse
import com.ssafy.s309.data.model.GlucosePrediction
import com.ssafy.s309.data.model.PredictRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PredictApi {
    @POST("api/predict/glucose")
    suspend fun predictGlucose(
        @Body request: PredictRequest,
    ): GlucosePrediction

    @POST("api/predict/glucose/compare")
    suspend fun compareGlucose(
        @Body request: GlucoseCompareRequest,
    ): GlucoseCompareResponse

    @POST("api/predict/compare/explain")
    suspend fun explainCompare(
        @Body request: CompareExplainRequest,
    ): CompareExplainResponse

    /**
     * 사진 한 장 → CV 인식 + foods 영양정보 매칭 + 식전 혈당 예측까지 단일 호출.
     * BE 측 FromImagePredictionService 가 오케스트레이션.
     */
    @Multipart
    @POST("api/predict/glucose/from-image")
    suspend fun predictFromImage(
        @Part image: MultipartBody.Part,
    ): FromImagePredictResponse
}
