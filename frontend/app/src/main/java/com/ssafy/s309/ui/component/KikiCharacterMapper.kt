package com.ssafy.s309.ui.component

import androidx.annotation.DrawableRes
import com.ssafy.s309.R

enum class KikiSymptom { THIRST, TIRED, BLUR }

object KikiCharacterMapper {
    private const val LOW_THRESHOLD = 70

    private data class Thresholds(val ok: Int, val mild: Int, val moderate: Int)

    private val THRESHOLDS =
        mapOf(
            "NORMAL" to Thresholds(ok = 140, mild = 180, moderate = 250),
            "T1D" to Thresholds(ok = 150, mild = 180, moderate = 250),
            "T2D" to Thresholds(ok = 140, mild = 180, moderate = 250),
        )

    @DrawableRes
    fun resolve(
        glucoseMgDl: Int?,
        trendRateMgDlPerMin: Float,
        diabetesType: String = "NORMAL",
    ): Int {
        val glucose = glucoseMgDl ?: return R.drawable.kiki_main_anim

        val adjusted = applyTrend(glucose, trendRateMgDlPerMin)
        val thresholds = THRESHOLDS[diabetesType] ?: THRESHOLDS.getValue("NORMAL")

        if (adjusted < LOW_THRESHOLD) return R.drawable.kiki_fell_off
        val severity = classifySeverity(adjusted, thresholds) ?: return R.drawable.kiki_main_anim
        val symptom = classifySymptom(trendRateMgDlPerMin)
        return drawableFor(severity, symptom)
    }

    private fun applyTrend(
        glucose: Int,
        rate: Float,
    ): Int {
        val offset =
            when {
                rate > 3f -> 30
                rate > 1f -> 15
                rate >= -1f -> 0
                rate >= -3f -> -10
                else -> -25
            }
        return (glucose + offset).coerceIn(40, 400)
    }

    private fun classifySeverity(
        adjusted: Int,
        t: Thresholds,
    ): String? =
        when {
            adjusted < t.ok -> null
            adjusted < t.mild -> "mild"
            adjusted < t.moderate -> "moderate"
            else -> "severe"
        }

    private fun classifySymptom(rate: Float): KikiSymptom =
        when {
            rate > 3f -> KikiSymptom.THIRST
            rate > 0f -> KikiSymptom.BLUR
            else -> KikiSymptom.TIRED
        }

    @DrawableRes
    private fun drawableFor(
        severity: String,
        symptom: KikiSymptom,
    ): Int =
        when (severity) {
            "mild" -> R.drawable.kiki_dehydrated_high
            "moderate" ->
                when (symptom) {
                    KikiSymptom.THIRST -> R.drawable.kiki_moderate_thirst
                    KikiSymptom.TIRED -> R.drawable.kiki_moderate_tired
                    KikiSymptom.BLUR -> R.drawable.kiki_moderate_blur
                }
            else ->
                when (symptom) {
                    KikiSymptom.THIRST -> R.drawable.kiki_severe_sick
                    KikiSymptom.TIRED -> R.drawable.kiki_severe_tired
                    KikiSymptom.BLUR -> R.drawable.kiki_severe_blur
                }
        }
}
