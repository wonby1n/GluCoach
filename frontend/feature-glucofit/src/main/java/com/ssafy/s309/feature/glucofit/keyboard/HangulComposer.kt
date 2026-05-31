package com.ssafy.s309.feature.glucofit.keyboard

/**
 * 두벌식 한글 조합 엔진 — history-stack 기반.
 *
 * 각 자모 입력 단계를 State로 history에 push하고,
 * backspace 시 pop하여 복합 종성(ㄳ ㄵ ㄺ 등)의 역분해를 정확하게 처리한다.
 *
 * 기존 구현의 문제:
 *   - jong > 0 → jong = 0 단순 초기화 → "닭"에서 backspace 시 "달"이 아닌 "다"로 점프
 * 해결:
 *   - 종성 조합 전 State(jong=ㄹ)를 먼저 push, 조합 후 State(jong=ㄺ, pair=(ㄹ,ㄱ)) push
 *   - backspace = removeAt(last) → 자연스럽게 이전 State로 복원
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

        // 복합 종성 조합표: (첫 자음, 둘째 자음) → 복합 종성 코드
        val JONG_COMBINE: Map<Pair<Char, Char>, Char> =
            mapOf(
                ('ㄱ' to 'ㅅ') to 'ㄳ',
                ('ㄴ' to 'ㅈ') to 'ㄵ',
                ('ㄴ' to 'ㅎ') to 'ㄶ',
                ('ㄹ' to 'ㄱ') to 'ㄺ',
                ('ㄹ' to 'ㅁ') to 'ㄻ',
                ('ㄹ' to 'ㅂ') to 'ㄼ',
                ('ㄹ' to 'ㅅ') to 'ㄽ',
                ('ㄹ' to 'ㅌ') to 'ㄾ',
                ('ㄹ' to 'ㅍ') to 'ㄿ',
                ('ㄹ' to 'ㅎ') to 'ㅀ',
                ('ㅂ' to 'ㅅ') to 'ㅄ',
            )
    }

    /**
     * 음절 조합 상태 스냅샷. history에 push/pop하며 조합 상태를 추적한다.
     * jongPair: 복합 종성일 경우 구성 쌍 (첫째, 둘째). 역분해 및 연음에 활용.
     */
    private data class State(
        val cho: Int = -1,
        val jung: Int = -1,
        val jong: Int = 0,
        val jongPair: Pair<Char, Char>? = null,
    )

    private val history = mutableListOf<State>()
    private val current: State get() = history.lastOrNull() ?: State()

    val composing: String get() = stateToString(current)

    private fun stateToString(s: State): String =
        when {
            s.cho < 0 && s.jung < 0 -> ""
            s.cho >= 0 && s.jung < 0 -> CHO[s.cho].toString()
            s.cho < 0 -> JUNG[s.jung].toString()
            else -> (0xAC00 + s.cho * 21 * 28 + s.jung * 28 + s.jong).toChar().toString()
        }

    sealed class Result {
        data class Compose(val commit: String, val composing: String) : Result()

        data class Backspace(val composing: String) : Result()

        object DeleteChar : Result()
    }

    fun input(jamo: Char): Result {
        val isV = jamo in JUNG
        val isC = jamo in CHO
        val c = current

        return when {
            // 비어있음 + 자음 → 초성
            isC && c.cho < 0 && c.jung < 0 -> {
                push(State(cho = CHO.indexOf(jamo)))
                Result.Compose("", composing)
            }
            // 비어있음 or 초성 없음 + 모음 → 중성 단독
            isV && c.cho < 0 -> {
                push(State(jung = JUNG.indexOf(jamo)))
                Result.Compose("", composing)
            }
            // 초성만 + 모음 → 초중 조합
            isV && c.cho >= 0 && c.jung < 0 -> {
                push(State(cho = c.cho, jung = JUNG.indexOf(jamo)))
                Result.Compose("", composing)
            }
            // 초중 + 종성 없음 + 자음 → 종성 후보
            isC && c.cho >= 0 && c.jung >= 0 && c.jong == 0 -> {
                val ji = CONS_TO_JONG[jamo]
                if (ji != null) {
                    push(State(c.cho, c.jung, ji))
                    Result.Compose("", composing)
                } else {
                    startNew(jamo)
                }
            }
            // 단일 종성 + 자음 → 복합 종성 시도 or 새 음절
            isC && c.jong > 0 && c.jongPair == null -> {
                val jongChar = JONG[c.jong]
                val combined = JONG_COMBINE[jongChar to jamo]
                val combinedIdx = combined?.let { JONG.indexOf(it) }?.takeIf { it > 0 }
                if (combinedIdx != null) {
                    push(State(c.cho, c.jung, combinedIdx, jongPair = jongChar to jamo))
                    Result.Compose("", composing)
                } else {
                    startNew(jamo)
                }
            }
            // 복합 종성 + 자음 → 무조건 새 음절
            isC && c.jong > 0 && c.jongPair != null -> startNew(jamo)
            // 단일 종성 + 모음 → 연음: 종성이 다음 초성으로 이동
            isV && c.jong > 0 && c.jongPair == null -> {
                val jongChar = JONG[c.jong]
                val newChoIdx = JONG_TO_CHO[jongChar] ?: return startNew(jamo)
                val committed = stateToString(State(c.cho, c.jung, 0))
                history.clear()
                push(State(cho = newChoIdx))
                push(State(cho = newChoIdx, jung = JUNG.indexOf(jamo)))
                Result.Compose(committed, composing)
            }
            // 복합 종성 + 모음 → 연음: 첫 자음 종성 유지, 둘째 자음이 다음 초성으로
            isV && c.jong > 0 && c.jongPair != null -> {
                val (first, second) = c.jongPair!!
                val remainIdx = CONS_TO_JONG[first] ?: return startNew(jamo)
                val newChoIdx = JONG_TO_CHO[second] ?: return startNew(jamo)
                val committed = stateToString(State(c.cho, c.jung, remainIdx))
                history.clear()
                push(State(cho = newChoIdx))
                push(State(cho = newChoIdx, jung = JUNG.indexOf(jamo)))
                Result.Compose(committed, composing)
            }
            else -> startNew(jamo)
        }
    }

    fun backspace(): Result =
        when (history.size) {
            0 -> Result.DeleteChar
            1 -> {
                history.clear()
                Result.DeleteChar
            } // GlucoseKeyboard에서 hadComposing=true로 처리
            else -> {
                history.removeAt(history.lastIndex)
                Result.Backspace(composing)
            }
        }

    fun flush(): String {
        val text = composing
        history.clear()
        return text
    }

    fun isEmpty(): Boolean = history.isEmpty()

    fun reset() = history.clear()

    private fun push(state: State) {
        history.add(state)
        if (history.size > 20) history.removeAt(0) // 안전 한계
    }

    private fun startNew(jamo: Char): Result {
        val committed = composing
        history.clear()
        when {
            jamo in CHO -> push(State(cho = CHO.indexOf(jamo)))
            jamo in JUNG -> push(State(jung = JUNG.indexOf(jamo)))
        }
        return Result.Compose(committed, composing)
    }
}
