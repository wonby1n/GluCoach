package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.FoodSearchItem
import retrofit2.http.GET
import retrofit2.http.Query

interface FoodApi {
    @GET("api/foods/search")
    suspend fun searchFoods(
        @Query("q") query: String,
    ): List<FoodSearchItem>

    @GET("api/food-grades")
    suspend fun getFoodGrades(): List<FoodGradeResponse>
}
