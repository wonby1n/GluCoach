package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.HealthApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository
    @Inject
    constructor(
        private val healthApi: HealthApi,
    ) {
        suspend fun createMeal(
            foodId: Int,
            recordedAt: LocalDateTime,
            photoFile: File?,
        ): Result<Int> =
            runCatching {
                val json = """{"foodId":$foodId,"recordedAt":"${recordedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}","memo":null}"""
                val requestBody = json.toRequestBody("application/json".toMediaType())

                val imagePart =
                    photoFile?.let {
                        MultipartBody.Part.createFormData(
                            "image",
                            it.name,
                            it.asRequestBody("image/jpeg".toMediaType()),
                        )
                    }

                healthApi.createMeal(requestBody, imagePart).mealId
            }
    }
