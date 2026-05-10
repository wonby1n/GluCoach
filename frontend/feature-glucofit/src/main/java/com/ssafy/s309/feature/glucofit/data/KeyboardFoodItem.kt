package com.ssafy.s309.feature.glucofit.data

/**
 * IME 측 음식 표현. app 모듈이 keyboard_foods.json에 직렬화한 형태와 1:1 매칭.
 *
 * grade: "S" / "A" / "B" / "C" / "D" / null (미평가)
 */
data class KeyboardFoodItem(
    val name: String,
    val category: String? = null,
    val grade: String? = null,
)
