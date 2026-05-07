"""LLM 호출 단독 테스트 스크립트. ai/ 디렉토리에서 실행: python test_llm.py"""

from app.schemas.report import WeeklyReportRequest, HourlyGlucose, DailyGlucose, FoodItem
from app.report.llm_caller import call_weekly_report_llm

req = WeeklyReportRequest(
    user_id=1,
    week_start="2025-01-06",
    week_end="2025-01-12",
    user_name="테스트",
    diabetes_type="2",
    target_low=70.0,
    target_high=180.0,
    avg_glucose=145.3,
    min_glucose=72.0,
    max_glucose=243.0,
    glucose_sd=38.5,
    time_in_range=62.4,
    time_above_range=33.1,
    time_below_range=4.5,
    hourly_avg_glucose=[
        HourlyGlucose(hour=h, avg=avg) for h, avg in [
            (0,118),(1,112),(2,108),(3,105),(4,107),(5,110),
            (6,115),(7,132),(8,165),(9,155),(10,148),(11,142),
            (12,138),(13,175),(14,210),(15,195),(16,168),(17,155),
            (18,145),(19,182),(20,220),(21,198),(22,165),(23,135),
        ]
    ],
    daily_avg_glucose=[
        DailyGlucose(date="2025-01-06", avg=138.2, min=82.0, max=215.0),
        DailyGlucose(date="2025-01-07", avg=142.5, min=75.0, max=228.0),
        DailyGlucose(date="2025-01-08", avg=151.3, min=88.0, max=243.0),
        DailyGlucose(date="2025-01-09", avg=139.8, min=72.0, max=198.0),
        DailyGlucose(date="2025-01-10", avg=148.1, min=80.0, max=221.0),
        DailyGlucose(date="2025-01-11", avg=143.6, min=78.0, max=210.0),
        DailyGlucose(date="2025-01-12", avg=147.0, min=85.0, max=235.0),
    ],
    good_foods=[FoodItem(food_name="두부", avg_slope=0.8), FoodItem(food_name="계란", avg_slope=0.6)],
    bad_foods=[FoodItem(food_name="흰쌀밥", avg_slope=4.2), FoodItem(food_name="라면", avg_slope=5.1)],
    meal_count=18,
    weekly_avg_steps=5200.0,
    weekly_total_calories=1820.0,
    weekly_avg_sleep_minutes=390.0,
    medication_count=12,
)

print("LLM 호출 중...")
summary, suggest = call_weekly_report_llm(req)
print("\n=== ai_summary ===")
print(summary)
print("\n=== ai_suggest ===")
print(suggest)
