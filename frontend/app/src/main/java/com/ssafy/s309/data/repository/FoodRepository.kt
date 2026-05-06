package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.AiFoodApi
import com.ssafy.s309.data.api.FoodApi
import com.ssafy.s309.data.model.DetectResponse
import com.ssafy.s309.data.model.FoodSearchItem
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
        private val aiFoodApi: AiFoodApi,
    ) {
        suspend fun searchFoods(query: String): Result<List<FoodSearchItem>> = runCatching { foodApi.searchFoods(query) }

        suspend fun detectFood(photoFile: File): Result<DetectResponse> =
            runCatching {
                val imagePart =
                    MultipartBody.Part.createFormData(
                        "file",
                        photoFile.name,
                        photoFile.asRequestBody("image/jpeg".toMediaType()),
                    )
                aiFoodApi.detectFood(imagePart)
            }
    }
