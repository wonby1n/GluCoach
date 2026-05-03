package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.GlucoseCompareRequest
import com.ssafy.s309.data.model.GlucoseCompareResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface PredictApi {
    @POST("api/predict/glucose/compare")
    suspend fun compareGlucose(
        @Body request: GlucoseCompareRequest,
    ): GlucoseCompareResponse
}
