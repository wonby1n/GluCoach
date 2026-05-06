package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodSearchItem(
    val id: Long,
    val name: String,
    val category: String? = null,
    val kcal: Double? = null,
    val carbsG: Double? = null,
    val sugarG: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val saturatedFatG: Double? = null,
    val transFatG: Double? = null,
    val cholesterolMg: Double? = null,
    val sodiumMg: Double? = null,
    val servingSize: Double? = null,
    val isCustomized: Boolean = false,
)
