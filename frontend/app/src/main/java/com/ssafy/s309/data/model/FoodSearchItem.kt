package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodSearchItem(
    val id: Long,
    val name: String,
    val category: String,
    val kcal: Int,
    val carbsG: Int,
    val sugarG: Int,
    val proteinG: Int,
    val fatG: Int,
    val fiberG: Int,
    val saturatedFatG: Int,
    val transFatG: Int,
    val cholesterolMg: Int,
    val sodiumMg: Int,
    val servingSize: Int,
    val isCustomized: Boolean,
)
