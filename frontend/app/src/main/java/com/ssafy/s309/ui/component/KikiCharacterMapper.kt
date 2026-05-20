package com.ssafy.s309.ui.component

import androidx.annotation.DrawableRes
import com.ssafy.s309.R

enum class KikiSymptom { THIRST, TIRED, BLUR }

object KikiCharacterMapper {
    private const val LOW_THRESHOLD = 70

    private data class Thresholds(val high: Int, val severe: Int)

    private val THRESHOLDS =
        mapOf(
            "NORMAL" to Thresholds(high = 180, severe = 250),
            "T1D" to Thresholds(high = 180, severe = 250),
            "T2D" to Thresholds(high = 180, severe = 250),
        )

    @DrawableRes
    fun resolve(
        glucoseMgDl: Int?,
        trendRateMgDlPerMin: Float,
        diabetesType: String = "NORMAL",
    ): Int {
        val glucose = glucoseMgDl ?: return R.drawable.kiki_main_anim
        val thresholds = THRESHOLDS[diabetesType] ?: THRESHOLDS.getValue("NORMAL")

        if (glucose < LOW_THRESHOLD) return R.drawable.kiki_fell_off
        val severity = classifySeverity(glucose, thresholds) ?: return R.drawable.kiki_main_anim
        val symptom = classifySymptom(trendRateMgDlPerMin)
        return drawableFor(severity, symptom)
    }

    private fun classifySeverity(
        glucose: Int,
        t: Thresholds,
    ): String? =
        when {
            glucose < t.high -> null
            glucose < t.severe -> "moderate"
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
