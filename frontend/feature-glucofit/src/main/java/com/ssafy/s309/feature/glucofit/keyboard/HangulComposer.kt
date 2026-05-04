package com.ssafy.s309.feature.glucofit.keyboard

/**
 * 두벌식 한글 조합 엔진
 * Unicode Hangul: 가 = 0xAC00 + 초성*21*28 + 중성*28 + 종성
 */
class HangulComposer {
    companion object {
        val CHO = listOf('ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')
        val JUNG = listOf('ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ')
        val JONG =
            listOf(' ', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')

        val CONS_TO_JONG =
            mapOf(
                'ㄱ' to 1, 'ㄲ' to 2, 'ㄴ' to 4, 'ㄷ' to 7, 'ㄹ' to 8,
                'ㅁ' to 16, 'ㅂ' to 17, 'ㅅ' to 19, 'ㅆ' to 20, 'ㅇ' to 21,
                'ㅈ' to 22, 'ㅊ' to 23, 'ㅋ' to 24, 'ㅌ' to 25, 'ㅍ' to 26, 'ㅎ' to 27,
            )
        val JONG_TO_CHO =
            mapOf(
                'ㄱ' to 0, 'ㄲ' to 1, 'ㄴ' to 2, 'ㄷ' to 3, 'ㄹ' to 5,
                'ㅁ' to 6, 'ㅂ' to 7, 'ㅅ' to 9, 'ㅆ' to 10, 'ㅇ' to 11,
                'ㅈ' to 12, 'ㅊ' to 14, 'ㅋ' to 15, 'ㅌ' to 16, 'ㅍ' to 17, 'ㅎ' to 18,
            )
    }

    private var cho = -1
    private var jung = -1
    private var jong = 0

    val composing: String get() {
        if (cho < 0 && jung < 0) return ""
        if (cho >= 0 && jung < 0) return CHO[cho].toString()
        if (cho < 0 && jung >= 0) return JUNG[jung].toString()
        return (0xAC00 + cho * 21 * 28 + jung * 28 + jong).toChar().toString()
    }

    sealed class Result {
        data class Compose(val commit: String, val composing: String) : Result()

        data class Backspace(val composing: String) : Result()

        object DeleteChar : Result()
    }

    fun input(jamo: Char): Result {
        val isV = jamo in JUNG
        val isC = jamo in CHO

        return when {
            isV && cho >= 0 && jung < 0 -> {
                jung = JUNG.indexOf(jamo)
                jong = 0
                Result.Compose("", composing)
            }
            isC && cho >= 0 && jung >= 0 && jong == 0 -> {
                val ji = CONS_TO_JONG[jamo]
                if (ji != null) {
                    jong = ji
                    Result.Compose("", composing)
                } else {
                    startNew(jamo)
                }
            }
            isV && jong > 0 -> {
                val jongChar = JONG[jong]
                val newCho = JONG_TO_CHO[jongChar]
                if (newCho != null) {
                    jong = 0
                    val committed = composing
                    cho = newCho
                    jung = JUNG.indexOf(jamo)
                    jong = 0
                    Result.Compose(committed, composing)
                } else {
                    startNew(jamo)
                }
            }
            isC && jong > 0 -> startNew(jamo)
            isC && cho < 0 -> {
                cho = CHO.indexOf(jamo)
                jung = -1
                jong = 0
                Result.Compose("", composing)
            }
            isV && cho < 0 -> {
                jung = JUNG.indexOf(jamo)
                Result.Compose("", composing)
            }
            else -> startNew(jamo)
        }
    }

    private fun startNew(jamo: Char): Result {
        val committed = composing
        reset()
        input(jamo)
        return Result.Compose(committed, composing)
    }

    fun backspace(): Result {
        return when {
            jong > 0 -> {
                jong = 0
                Result.Backspace(composing)
            }
            jung >= 0 -> {
                jung = -1
                Result.Backspace(if (cho >= 0) CHO[cho].toString() else "")
            }
            cho >= 0 -> {
                reset()
                Result.DeleteChar
            }
            else -> Result.DeleteChar
        }
    }

    fun flush(): String {
        val text = composing
        reset()
        return text
    }

    fun isEmpty() = cho < 0 && jung < 0

    fun reset() {
        cho = -1
        jung = -1
        jong = 0
    }
}
