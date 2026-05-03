package com.ssafy.s309.data.repository

import com.ssafy.s309.data.api.FoodApi
import com.ssafy.s309.data.model.FoodSearchItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository
    @Inject
    constructor(
        private val foodApi: FoodApi,
    ) {
        suspend fun searchFoods(query: String): Result<List<FoodSearchItem>> = runCatching { foodApi.searchFoods(query) }
    }
