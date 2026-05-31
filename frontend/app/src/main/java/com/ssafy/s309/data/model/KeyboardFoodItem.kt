package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KeyboardFoodItem(
    val name: String,
    val displayName: String? = null,
    val category: String? = null,
    val grade: String? = null,
)
