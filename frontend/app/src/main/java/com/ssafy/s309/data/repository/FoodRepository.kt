package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.FoodApi
import com.ssafy.s309.data.api.PredictApi
import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.FromImagePredictResponse
import com.ssafy.s309.data.model.MealRecordResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository
    @Inject
    constructor(
        private val foodApi: FoodApi,
        private val predictApi: PredictApi,
    ) {
        suspend fun searchFoods(query: String): Result<List<FoodSearchItem>> = runCatching { foodApi.searchFoods(query) }

        suspend fun getFoodGrades(): Result<List<FoodGradeResponse>> = runCatching { foodApi.getFoodGrades() }

        suspend fun getFoodMealHistory(foodId: Int): Result<List<MealRecordResponse>> = runCatching { foodApi.getFoodMealHistory(foodId) }

        /**
         * 사진 통합 식전 예측 — BE 가 CV 인식 + foods 매칭 + 예측까지 한 번에 처리.
         * AI 직접 호출 (구 detectFood) 대체.
         */
        suspend fun predictFromImage(photoFile: File): Result<FromImagePredictResponse> =
            runCatching {
                val imagePart =
                    MultipartBody.Part.createFormData(
                        "image",
                        photoFile.name,
                        photoFile.asRequestBody("image/jpeg".toMediaType()),
                    )
                predictApi.predictFromImage(imagePart)
            }
    }
