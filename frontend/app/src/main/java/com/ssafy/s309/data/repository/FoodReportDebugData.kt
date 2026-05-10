package com.ssafy.s309.data.repository

import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.MealRecordResponse

// ──────────────────────────────────────────────────────────
//  디버그 전용 mock 데이터. 배포 전 USE_FOOD_REPORT_MOCK = false 로 변경할 것.
// ──────────────────────────────────────────────────────────
internal const val USE_FOOD_REPORT_MOCK = true

internal val MOCK_FOOD_GRADES: List<FoodGradeResponse> =
    listOf(
        FoodGradeResponse(1, "연어샐러드", "S", 12.0, 8, "2026-05-06T08:30:00"),
        FoodGradeResponse(2, "브로콜리볶음", "S", 9.0, 5, "2026-05-01T12:00:00"),
        FoodGradeResponse(3, "닭가슴살", "S", 15.0, 6, "2026-05-04T18:30:00"),
        FoodGradeResponse(4, "아보카도", "S", 11.0, 3, "2026-04-22T09:00:00"),
        FoodGradeResponse(5, "두부김치", "A", 22.0, 4, "2026-05-05T12:30:00"),
        FoodGradeResponse(6, "달걀프라이", "A", 25.0, 12, "2026-05-07T07:45:00"),
        FoodGradeResponse(7, "고등어구이", "A", 28.0, 10, "2026-05-03T18:00:00"),
        FoodGradeResponse(8, "미역국", "A", 18.0, 7, "2026-05-06T12:00:00"),
        FoodGradeResponse(9, "잡곡밥", "B", 45.0, 9, "2026-05-07T12:30:00"),
        FoodGradeResponse(10, "김치찌개", "B", 42.0, 11, "2026-05-06T18:30:00"),
        FoodGradeResponse(11, "된장찌개", "B", 50.0, 6, "2026-05-02T18:00:00"),
        FoodGradeResponse(12, "비빔밥", "C", 65.0, 4, "2026-05-04T12:30:00"),
        FoodGradeResponse(13, "떡볶이", "C", 72.0, 3, "2026-04-28T15:00:00"),
        FoodGradeResponse(14, "짜장면", "C", 68.0, 5, "2026-05-01T12:00:00"),
        FoodGradeResponse(15, "치킨", "D", 85.0, 2, "2026-04-25T19:00:00"),
        FoodGradeResponse(16, "피자", "D", 90.0, 3, "2026-05-03T18:30:00"),
    )

internal fun mockFoodMealHistory(foodId: Int): List<MealRecordResponse> =
    when (foodId) {
        1 ->
            listOf(
                MealRecordResponse(101, 1, "연어샐러드", "신선한 연어", "2026-05-06T08:30:00", null),
                MealRecordResponse(102, 1, "연어샐러드", null, "2026-05-04T08:15:00", null),
                MealRecordResponse(103, 1, "연어샐러드", "아보카도 추가", "2026-05-01T12:00:00", null),
                MealRecordResponse(104, 1, "연어샐러드", null, "2026-04-28T08:30:00", null),
                MealRecordResponse(105, 1, "연어샐러드", null, "2026-04-28T12:20:00", null),
            )
        6 ->
            listOf(
                MealRecordResponse(201, 6, "달걀프라이", null, "2026-05-07T07:45:00", null),
                MealRecordResponse(202, 6, "달걀프라이", "반숙", "2026-05-06T07:30:00", null),
                MealRecordResponse(203, 6, "달걀프라이", null, "2026-05-05T08:00:00", null),
                MealRecordResponse(204, 6, "달걀프라이", null, "2026-05-03T07:50:00", null),
            )
        7 ->
            listOf(
                MealRecordResponse(301, 7, "고등어구이", null, "2026-05-03T18:00:00", null),
                MealRecordResponse(302, 7, "고등어구이", "무조림 곁들임", "2026-04-30T18:30:00", null),
                MealRecordResponse(303, 7, "고등어구이", null, "2026-04-28T12:00:00", null),
            )
        10 ->
            listOf(
                MealRecordResponse(401, 10, "김치찌개", null, "2026-05-06T18:30:00", null),
                MealRecordResponse(402, 10, "김치찌개", "참치 추가", "2026-05-04T12:00:00", null),
                MealRecordResponse(403, 10, "김치찌개", null, "2026-05-02T18:00:00", null),
            )
        else ->
            listOf(
                MealRecordResponse(
                    900,
                    foodId,
                    MOCK_FOOD_GRADES.firstOrNull { it.foodId == foodId }?.foodName,
                    null,
                    "2026-05-05T12:00:00",
                    null,
                ),
            )
    }
