package com.ssafy.s309.ui.component

import androidx.annotation.DrawableRes
import com.ssafy.s309.R

enum class KikiSymptom { THIRST, TIRED, BLUR }

object KikiCharacterMapper {
    @DrawableRes
    fun resolve(
        glucoseMgDl: Int?,
        diffFromPrevious: Int,
    ): Int {
        val glucose = glucoseMgDl ?: return R.drawable.kiki_main

        val severity = classifySeverity(glucose)
        if (severity == null) return R.drawable.kiki_main

        val symptom = classifySymptom(diffFromPrevious)
        return drawableFor(severity, symptom)
    }

    private fun classifySeverity(glucose: Int): String? =
        when {
            glucose < 140 -> null
            glucose < 180 -> "mild"
            glucose < 250 -> "moderate"
            else -> "severe"
        }

    private fun classifySymptom(diff: Int): KikiSymptom =
        when {
            diff > 15 -> KikiSymptom.THIRST
            diff in -5..5 -> KikiSymptom.TIRED
            diff > 5 -> KikiSymptom.BLUR
            else -> KikiSymptom.TIRED
        }

    @DrawableRes
    private fun drawableFor(
        severity: String,
        symptom: KikiSymptom,
    ): Int =
        when (severity) {
            "mild" ->
                when (symptom) {
                    KikiSymptom.THIRST -> R.drawable.kiki_mild_thirst
                    KikiSymptom.TIRED -> R.drawable.kiki_mild_tired
                    KikiSymptom.BLUR -> R.drawable.kiki_mild_blur
                }
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
