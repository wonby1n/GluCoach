package com.ssafy.s309.ui.component

import androidx.annotation.DrawableRes
import com.ssafy.s309.R

object FoodCategoryImageMapper {
    @DrawableRes
    fun getImageRes(
        category: String?,
        foodName: String = "",
    ): Int {
        exactNameMap[foodName]?.let { return it }
        categoryMap[category]?.let { return it }
        return matchByName(foodName)
    }

    private val exactNameMap =
        mapOf(
            "연어구이" to R.drawable.food_salmon_grill,
            "달걀찜" to R.drawable.food_steamed_egg,
            "토마토소스스파게티" to R.drawable.food_tomato_spaghetti,
            "스파게티" to R.drawable.food_tomato_spaghetti,
        )

    private val categoryMap =
        mapOf(
            "곡류" to R.drawable.food_grains_rice,
            "밥류" to R.drawable.food_grains_rice,
            "두류" to R.drawable.food_grains_rice,
            "장류" to R.drawable.food_grains_rice,
            "면 및 만두류" to R.drawable.food_pasta_noodles,
            "빵 및 과자류" to R.drawable.food_bakery_carbs,
            "구이류" to R.drawable.food_meat_poultry,
            "볶음류" to R.drawable.food_meat_poultry,
            "전·적 및 부침류" to R.drawable.food_meat_poultry,
            "찜류" to R.drawable.food_meat_poultry,
            "튀김류" to R.drawable.food_meat_poultry,
            "조림류" to R.drawable.food_meat_poultry,
            "수·조·어·육류" to R.drawable.food_meat_poultry,
            "젓갈류" to R.drawable.food_seafood,
            "채소" to R.drawable.food_greens,
            "나물·숙채류" to R.drawable.food_greens,
            "생채·무침류" to R.drawable.food_greens,
            "김치류" to R.drawable.food_greens,
            "장아찌·절임류" to R.drawable.food_greens,
            "과일류" to R.drawable.food_fruits,
            "유제품류 및 빙과류" to R.drawable.food_dairy,
            "음료 및 차류" to R.drawable.food_beverages,
            "국 및 탕류" to R.drawable.food_soup,
            "찌개 및 전골류" to R.drawable.food_soup,
            "죽 및 스프류" to R.drawable.food_soup,
        )

    private val nameKeywords =
        listOf(
            listOf("밥", "죽", "떡볶이", "김밥", "주먹밥", "비빔밥", "볶음밥", "덮밥") to R.drawable.food_grains_rice,
            listOf("면", "국수", "파스타", "라면", "우동", "소바", "냉면", "만두", "짜장", "짬뽕", "칼국수", "쌀국수") to R.drawable.food_pasta_noodles,
            listOf("빵", "토스트", "과자", "쿠키", "크래커", "베이글", "크루아상", "머핀", "와플", "팬케이크", "떡") to R.drawable.food_bakery_carbs,
            listOf(
                "고기", "소고기", "돼지", "닭", "치킨", "스테이크", "갈비", "삼겹",
                "불고기", "제육", "족발", "보쌈", "햄", "소시지", "베이컨",
            ) to R.drawable.food_meat_poultry,
            listOf(
                "생선", "연어", "참치", "새우", "조개", "해산물", "회", "초밥",
                "광어", "우럭", "고등어", "꽁치", "오징어", "문어", "게", "랍스터", "굴",
            ) to R.drawable.food_seafood,
            listOf("샐러드", "채소", "나물", "김치", "시금치", "브로콜리", "양배추", "상추", "오이", "당근", "피망") to R.drawable.food_greens,
            listOf("사과", "바나나", "딸기", "포도", "수박", "참외", "복숭아", "배", "귤", "오렌지", "망고", "키위", "블루베리", "체리") to R.drawable.food_fruits,
            listOf("우유", "치즈", "요거트", "요구르트", "버터", "크림") to R.drawable.food_dairy,
            listOf("음료", "커피", "차", "주스", "콜라", "사이다", "에이드", "스무디", "밀크쉐이크") to R.drawable.food_beverages,
            listOf("국", "탕", "찌개", "전골", "스프", "미역국", "된장국", "김치찌개", "부대찌개") to R.drawable.food_soup,
            listOf("케이크", "초콜릿", "아이스크림", "마카롱", "디저트", "푸딩", "젤리", "사탕", "캔디", "타르트", "도넛") to R.drawable.food_sweets,
        )

    private fun matchByName(name: String): Int {
        for ((keywords, resId) in nameKeywords) {
            if (keywords.any { name.contains(it) }) return resId
        }
        return R.drawable.food_grains_rice
    }
}
