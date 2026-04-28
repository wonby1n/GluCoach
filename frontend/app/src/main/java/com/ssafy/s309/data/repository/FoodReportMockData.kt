package com.ssafy.s309.data.repository

import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.model.FoodTrend
import com.ssafy.s309.data.model.GradeFoodItem

object FoodReportMockData {
    val gradeInfoList =
        listOf(
            FoodGradeInfo(
                "S",
                "내 몸에 딱 맞아요",
                "혈당 상승 0~20 mg/dL",
                "이 음식들은 혈당이 거의 안 오를 만큼 잘 맞아요.\n자주 드시면 혈당 관리에 최고예요!",
                12,
            ),
            FoodGradeInfo(
                "A",
                "잘 맞는 편이에요",
                "혈당 상승 20~40 mg/dL",
                "이 음식들은 혈당이 소폭 오르지만 안정적이에요.\n안심하고 드셔도 좋아요!",
                20,
            ),
            FoodGradeInfo(
                "B",
                "가끔은 괜찮아요",
                "혈당 상승 40~60 mg/dL",
                "이 음식들은 혈당이 어느 정도 오르는 편이에요.\n가끔 드시는 건 괜찮지만 양 조절이 필요해요!",
                18,
            ),
            FoodGradeInfo(
                "C",
                "주의가 필요해요",
                "혈당 상승 60~80 mg/dL",
                "이 음식들은 혈당을 꽤 올리는 편이에요.\n드실 때 양을 줄이거나 다른 음식과 함께 드세요!",
                12,
            ),
            FoodGradeInfo(
                "D",
                "가급적 피해주세요",
                "혈당 상승 80~100 mg/dL",
                "이 음식들은 혈당을 많이 올려요.\n가급적 피하시거나 소량만 드세요!",
                5,
            ),
            FoodGradeInfo(
                "F",
                "드시지 않는 게 좋아요",
                "혈당 상승 100+ mg/dL",
                "이 음식들은 혈당을 급격히 올려요.\n건강을 위해 드시지 않는 것을 권장해요!",
                3,
            ),
        )

    val gradeFoodItems =
        mapOf(
            "S" to
                listOf(
                    GradeFoodItem("연어 샐러드", R.drawable.salmon_salad, 8, "3일 전", 18, FoodTrend.DOWN, 8),
                    GradeFoodItem("달걀프라이", R.drawable.grilled_mackerel, 12, "오늘", 12, FoodTrend.STABLE, 12),
                    GradeFoodItem("브로콜리볶음", R.drawable.keto_kimbap, 5, "1주 전", 9, FoodTrend.DOWN, 6),
                    GradeFoodItem("고등어구이", R.drawable.grilled_mackerel, 6, "4일 전", 15, FoodTrend.DOWN, 6),
                    GradeFoodItem("아보카도", R.drawable.salmon_salad, 3, "2주 전", 11, FoodTrend.STABLE, 3),
                ),
            "A" to
                listOf(
                    GradeFoodItem("키토 김밥", R.drawable.keto_kimbap, 10, "2일 전", 25, FoodTrend.DOWN, 10),
                    GradeFoodItem("연어구이", R.drawable.salmon_salad, 7, "5일 전", 30, FoodTrend.STABLE, 7),
                    GradeFoodItem("두부 샐러드", R.drawable.keto_kimbap, 4, "1주 전", 22, FoodTrend.DOWN, 4),
                    GradeFoodItem("닭가슴살", R.drawable.grilled_mackerel, 9, "오늘", 35, FoodTrend.DOWN, 9),
                ),
            "B" to
                listOf(
                    GradeFoodItem("현미밥", R.drawable.keto_kimbap, 15, "오늘", 45, FoodTrend.STABLE, 15),
                    GradeFoodItem("잡곡밥", R.drawable.keto_kimbap, 12, "1일 전", 50, FoodTrend.UP, 12),
                    GradeFoodItem("고구마", R.drawable.salmon_salad, 8, "3일 전", 55, FoodTrend.STABLE, 8),
                ),
            "C" to
                listOf(
                    GradeFoodItem("짬뽕", R.drawable.jjambbong, 3, "3일 전", 65, FoodTrend.UP, 3),
                    GradeFoodItem("우동", R.drawable.jjajangmyeon, 5, "1주 전", 70, FoodTrend.STABLE, 5),
                ),
            "D" to
                listOf(
                    GradeFoodItem("짜장면", R.drawable.jjajangmyeon, 4, "5일 전", 85, FoodTrend.UP, 4),
                    GradeFoodItem("떡볶이", R.drawable.keto_kimbap, 2, "2주 전", 95, FoodTrend.UP, 2),
                ),
            "F" to
                listOf(
                    GradeFoodItem("탕수육", R.drawable.jjajangmyeon, 1, "3주 전", 110, FoodTrend.UP, 1),
                ),
        )
}
