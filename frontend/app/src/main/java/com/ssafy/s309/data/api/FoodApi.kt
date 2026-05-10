package com.ssafy.s309.data.api

import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.KeyboardFoodItem
import com.ssafy.s309.data.model.MealRecordResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FoodApi {
    @GET("api/foods/search")
    suspend fun searchFoods(
        @Query("q") query: String,
    ): List<FoodSearchItem>

    @GET("api/food-grades")
    suspend fun getFoodGrades(): List<FoodGradeResponse>

    @GET("api/food-grades/{foodId}/meals")
    suspend fun getFoodMealHistory(
        @Path("foodId") foodId: Int,
    ): List<MealRecordResponse>

    /** IME 키보드용 — 전체 음식(이름+카테고리+사용자 등급). 1일 1회 동기화. */
    @GET("api/foods/keyboard")
    suspend fun getKeyboardFoods(): List<KeyboardFoodItem>
}
