package com.ssafy.s309.data.repository

import com.ssafy.s309.data.model.FoodGradeInfo

object FoodReportMockData {
    val gradeInfoList =
        listOf(
            FoodGradeInfo(
                "S",
                "내 몸에 딱 맞아요",
                "혈당 상승 0~20 mg/dL",
                "이 음식들은 혈당이 거의 안 오를 만큼 잘 맞아요.\n자주 드시면 혈당 관리에 최고예요!",
                0,
            ),
            FoodGradeInfo(
                "A",
                "잘 맞는 편이에요",
                "혈당 상승 20~40 mg/dL",
                "이 음식들은 혈당이 소폭 오르지만 안정적이에요.\n안심하고 드셔도 좋아요!",
                0,
            ),
            FoodGradeInfo(
                "B",
                "가끔은 괜찮아요",
                "혈당 상승 40~60 mg/dL",
                "이 음식들은 혈당이 어느 정도 오르는 편이에요.\n가끔 드시는 건 괜찮지만 양 조절이 필요해요!",
                0,
            ),
            FoodGradeInfo(
                "C",
                "주의가 필요해요",
                "혈당 상승 60~80 mg/dL",
                "이 음식들은 혈당을 꽤 올리는 편이에요.\n드실 때 양을 줄이거나 다른 음식과 함께 드세요!",
                0,
            ),
            FoodGradeInfo(
                "D",
                "가급적 피해주세요",
                "혈당 상승 80~100 mg/dL",
                "이 음식들은 혈당을 많이 올려요.\n가급적 피하시거나 소량만 드세요!",
                0,
            ),
            FoodGradeInfo(
                "F",
                "드시지 않는 게 좋아요",
                "혈당 상승 100+ mg/dL",
                "이 음식들은 혈당을 급격히 올려요.\n건강을 위해 드시지 않는 것을 권장해요!",
                0,
            ),
        )
}
