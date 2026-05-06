package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.DetectResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface AiFoodApi {
    @Multipart
    @POST("ai/api/v1/food/detect")
    suspend fun detectFood(
        @Part file: MultipartBody.Part,
        @Query("conf") conf: Float = 0.25f,
    ): DetectResponse
}
