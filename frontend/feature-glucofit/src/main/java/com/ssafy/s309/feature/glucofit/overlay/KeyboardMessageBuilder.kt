package com.ssafy.s309.feature.glucofit.overlay

import android.graphics.Color
import com.ssafy.s309.feature.glucofit.data.KeyboardFoodItem

/**
 * IME 배너 메시지 생성 — B-2 정책 (54 템플릿 + 변수 삽입).
 *
 * 차원: glucose(3) × grade(6) × meal_recency(3) = 54.
 *
 * <p>변수: {food}, {glucose}, {grade}, {minutes}.
 * 등급 null = 미체험 음식. 식사 시각 null = OVER 취급.
 */
object KeyboardMessageBuilder {
    data class Message(val text: String, val color: Int)

    private enum class GlucoseState { HIGH, NORMAL, LOW, UNKNOWN }

    private enum class MealRecency { RECENT, MID, OVER }

    private const val GLUCOSE_HIGH = 180
    private const val GLUCOSE_LOW = 70
    private const val MEAL_RECENT_MIN = 30
    private const val MEAL_MID_MIN = 120

    fun build(
        food: KeyboardFoodItem,
        glucose: Int?,
        lastMealAtMs: Long?,
    ): Message {
        if (glucose == null) {
            return Message("${food.name}, 혈당 측정 중이 아니에요", Color.parseColor("#9E9E9E"))
        }
        val gState = classifyGlucose(glucose)
        val recency = classifyRecency(lastMealAtMs)
        val grade = food.grade // S/A/B/C/D/null
        val minutes = lastMealAtMs?.let { ((System.currentTimeMillis() - it) / 60_000).toInt() } ?: -1
        val template = template(gState, grade, recency)
        val text =
            template
                .replace("{food}", food.name)
                .replace("{glucose}", glucose.toString())
                .replace("{grade}", grade ?: "?")
                .replace("{minutes}", minutes.toString())
        return Message(text, color(gState, grade))
    }

    private fun classifyGlucose(g: Int): GlucoseState =
        when {
            g > GLUCOSE_HIGH -> GlucoseState.HIGH
            g < GLUCOSE_LOW -> GlucoseState.LOW
            else -> GlucoseState.NORMAL
        }

    private fun classifyRecency(lastMealAtMs: Long?): MealRecency {
        if (lastMealAtMs == null) return MealRecency.OVER
        val minutes = (System.currentTimeMillis() - lastMealAtMs) / 60_000
        return when {
            minutes <= MEAL_RECENT_MIN -> MealRecency.RECENT
            minutes <= MEAL_MID_MIN -> MealRecency.MID
            else -> MealRecency.OVER
        }
    }

    private fun color(
        state: GlucoseState,
        grade: String?,
    ): Int =
        when (state) {
            GlucoseState.LOW -> Color.parseColor("#1E88E5")
            GlucoseState.HIGH ->
                when (grade) {
                    "D", "C" -> Color.parseColor("#E53935")
                    "B" -> Color.parseColor("#FF6F00")
                    "A", "S" -> Color.parseColor("#FB8C00")
                    else -> Color.parseColor("#FF6F00")
                }
            GlucoseState.NORMAL ->
                when (grade) {
                    "S", "A" -> Color.parseColor("#43A047")
                    "B" -> Color.parseColor("#71C1D2")
                    "C", "D" -> Color.parseColor("#FB8C00")
                    else -> Color.parseColor("#9E9E9E")
                }
            GlucoseState.UNKNOWN -> Color.parseColor("#9E9E9E")
        }

    private fun template(
        state: GlucoseState,
        grade: String?,
        recency: MealRecency,
    ): String =
        when (state) {
            GlucoseState.HIGH -> highTemplate(grade, recency)
            GlucoseState.NORMAL -> normalTemplate(grade, recency)
            GlucoseState.LOW -> lowTemplate(grade, recency)
            GlucoseState.UNKNOWN -> "{food} — 혈당 측정 중이 아니에요"
        }

    private fun highTemplate(
        grade: String?,
        recency: MealRecency,
    ): String =
        when (grade) {
            "D" ->
                when (recency) {
                    MealRecency.RECENT -> "방금 먹고 또? {food}(D)는 혈당 {glucose}에 최악이에요"
                    MealRecency.MID -> "혈당 {glucose}인데 {food}(D)는 피하세요"
                    MealRecency.OVER -> "혈당 {glucose} — {food}는 너에게 D등급이에요"
                }
            "C" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, 방금 식사 후라 {food}(C)는 부담"
                    MealRecency.MID -> "혈당 {glucose}, {food}(C) — 양 줄이세요"
                    MealRecency.OVER -> "혈당 {glucose}에 {food}(C)는 주의해야 해요"
                }
            "B" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, 방금 식사 — {food}(B)면 소량만"
                    MealRecency.MID -> "{food}(B), 혈당 {glucose}이면 양 조심"
                    MealRecency.OVER -> "혈당 {glucose} — {food}(B)는 적당히"
                }
            "A" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}고 방금 식사 — {food}(A)도 미루세요"
                    MealRecency.MID -> "혈당 {glucose}이지만 {food}(A)는 너에겐 안전한 편"
                    MealRecency.OVER -> "{food}(A) — 너한테 좋은 음식, 혈당 {glucose}여도 무리 적음"
                }
            "S" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, 30분 전 식사. {food}(S)도 잠깐 기다리세요"
                    MealRecency.MID -> "혈당 {glucose}, {food}(S) — 너에겐 베스트지만 양은 조심"
                    MealRecency.OVER -> "혈당 {glucose}에 {food}(S) 좋은 선택이에요"
                }
            else ->
                when (recency) {
                    MealRecency.RECENT -> "방금 먹고 또? 혈당 {glucose}에 {food}는 데이터 없어요"
                    MealRecency.MID -> "혈당 {glucose}, {food}는 첫 시도 — 양 조심"
                    MealRecency.OVER -> "혈당 {glucose}, {food}는 미체험 음식이에요"
                }
        }

    private fun normalTemplate(
        grade: String?,
        recency: MealRecency,
    ): String =
        when (grade) {
            "D" ->
                when (recency) {
                    MealRecency.RECENT -> "방금 식사하고 {food}(D)? 혈당 {glucose}여도 무리에요"
                    MealRecency.MID -> "혈당 {glucose}, {food}(D)는 너한테 안 좋아요"
                    MealRecency.OVER -> "{food}는 D등급, 혈당 {glucose}이지만 양 조심"
                }
            "C" ->
                when (recency) {
                    MealRecency.RECENT -> "방금 먹고 {food}(C)는 좀 그래요"
                    MealRecency.MID -> "혈당 {glucose}, {food}(C) — 적당히 드세요"
                    MealRecency.OVER -> "{food}(C), 혈당 {glucose}에 보통은 OK"
                }
            "B" ->
                when (recency) {
                    MealRecency.RECENT -> "{food}(B), 30분 전 식사 후라 양 조심"
                    MealRecency.MID -> "{food}(B), 혈당 {glucose}이면 평소처럼 OK"
                    MealRecency.OVER -> "{food}(B) — 평범한 선택이에요"
                }
            "A" ->
                when (recency) {
                    MealRecency.RECENT -> "방금 먹었는데 {food}(A)? 양만 조심"
                    MealRecency.MID -> "{food}(A), 혈당 {glucose}여서 좋은 타이밍"
                    MealRecency.OVER -> "{food}(A) — 너한테 잘 맞는 음식"
                }
            "S" ->
                when (recency) {
                    MealRecency.RECENT -> "{food}(S) 베스트 — 다만 방금 먹었으니 양만 조심"
                    MealRecency.MID -> "{food}(S), 혈당 {glucose}에 완벽한 선택"
                    MealRecency.OVER -> "{food}(S) — 너에겐 최고 음식, 마음껏"
                }
            else ->
                when (recency) {
                    MealRecency.RECENT -> "방금 식사 후 {food} — 첫 시도면 소량부터"
                    MealRecency.MID -> "{food}는 처음 — 혈당 추세 보면서 드세요"
                    MealRecency.OVER -> "{food}는 너한테 새 음식 — 양 조심하면서"
                }
        }

    private fun lowTemplate(
        grade: String?,
        recency: MealRecency,
    ): String =
        when (grade) {
            "D" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}로 낮음! {food}(D)라도 빠르게"
                    MealRecency.MID -> "저혈당 {glucose} — {food}(D)도 일단 드세요"
                    MealRecency.OVER -> "혈당 {glucose}, {food}(D)지만 지금은 당분 우선"
                }
            "C" ->
                when (recency) {
                    MealRecency.RECENT -> "저혈당 {glucose}! {food}(C)로 빠르게 대응"
                    MealRecency.MID -> "혈당 {glucose} — {food}(C)도 보충용으로 OK"
                    MealRecency.OVER -> "{food}(C), 저혈당엔 일단 드세요"
                }
            "B" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, {food}(B) 적당해요"
                    MealRecency.MID -> "저혈당 {glucose}에 {food}(B) 무난"
                    MealRecency.OVER -> "{food}(B), 혈당 {glucose}로 낮으니 충분히"
                }
            "A" ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, {food}(A) 좋은 선택"
                    MealRecency.MID -> "저혈당 {glucose}, {food}(A) 적당히"
                    MealRecency.OVER -> "{food}(A), 혈당 {glucose}로 낮음 — 챙겨드세요"
                }
            "S" ->
                when (recency) {
                    MealRecency.RECENT -> "{food}(S)! 혈당 {glucose}에 안성맞춤"
                    MealRecency.MID -> "혈당 {glucose}, {food}(S) 베스트 선택"
                    MealRecency.OVER -> "{food}(S), 혈당 {glucose}로 낮음 — 든든히"
                }
            else ->
                when (recency) {
                    MealRecency.RECENT -> "혈당 {glucose}, {food}는 데이터 없지만 일단 보충"
                    MealRecency.MID -> "저혈당 {glucose} — {food} 미체험이지만 OK"
                    MealRecency.OVER -> "혈당 {glucose}로 낮음 — {food} 일단 드세요"
                }
        }
}
