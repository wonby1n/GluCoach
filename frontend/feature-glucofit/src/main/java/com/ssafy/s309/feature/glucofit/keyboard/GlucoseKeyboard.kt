package com.ssafy.s309.feature.glucofit.keyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import com.ssafy.s309.feature.glucofit.data.KeyboardFoodCache
import com.ssafy.s309.feature.glucofit.glucose.GlucoseSimulator
import com.ssafy.s309.feature.glucofit.overlay.OverlayBannerManager

class GlucoseKeyboard : InputMethodService() {
    private val currentText = StringBuilder()

    /** EditText 실제 내용 스냅샷. send 직전 마지막 비어있지 않은 값으로 갱신. */
    private var lastEditorText: String = ""
    private lateinit var keyContainer: LinearLayout
    private lateinit var btnLang: TextView
    private lateinit var inlineBanner: View
    private lateinit var inlineBannerDot: View
    private lateinit var inlineBannerText: TextView
    private val bannerManager by lazy { OverlayBannerManager(applicationContext) }
    private val hangul = HangulComposer()
    private var isKorean = true

    override fun onCreate() {
        super.onCreate()
        KeyboardFoodCache.load(applicationContext)
        // 메인 앱 미진입 상태에서도 IME 단독으로 혈당 시뮬레이터 가동.
        // 이미 실행 중이면 start() 내부 가드로 no-op.
        GlucoseSimulator.start(applicationContext)
    }

    private val KO_ROW1 = listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ")
    private val KO_ROW1S = listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "ㅛ", "ㅕ", "ㅑ", "ㅒ", "ㅖ")
    private val KO_ROW2 = listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ")
    private val KO_ROW3 = listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")

    private val EN_ROW1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val EN_ROW2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val EN_ROW3 = listOf("z", "x", "c", "v", "b", "n", "m")

    private var isShift = false

    override fun onCreateInputView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D1D3D8"))
            addView(buildInlineBanner().also { inlineBanner = it })
            addView(buildKeyboard().also { keyContainer = it })
        }
    }

    private fun buildInlineBanner(): View {
        val outer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#DDF3F8"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                visibility = View.GONE
            }
        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), Color.parseColor("#4EA8BC"))
                    }
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }

        val colorDot =
            View(this).apply {
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#9E9E9E"))
                    }
                layoutParams =
                    LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                        setMargins(0, 0, dp(10), 0)
                    }
            }.also { inlineBannerDot = it }

        val message =
            TextView(this).apply {
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1A1A1A"))
                layoutParams =
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }.also { inlineBannerText = it }

        val chevron =
            TextView(this).apply {
                text = "›"
                textSize = 22f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(dp(8), 0, dp(4), 0)
            }

        container.addView(colorDot)
        container.addView(message)
        container.addView(chevron)
        container.setOnClickListener {
            val intent =
                Intent().apply {
                    setClassName("com.ssafy.s309", "com.ssafy.s309.MainActivity")
                    putExtra("navigate_to", "food_report")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            startActivity(intent)
        }
        outer.addView(container)
        return outer
    }

    private fun updateInlineBanner(
        text: String,
        color: Int,
    ) {
        inlineBannerText.text = text
        inlineBannerText.setTextColor(Color.BLACK)
        (inlineBannerDot.background as? GradientDrawable)?.setColor(color)
        inlineBanner.visibility = View.VISIBLE
    }

    private fun hideInlineBanner() {
        if (::inlineBanner.isInitialized) {
            inlineBanner.visibility = View.GONE
        }
    }

    private fun buildKeyboard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(8), dp(3), dp(64))
            addView(buildLetterRows())
            addView(buildBottomRow())
        }
    }

    private fun buildLetterRows(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            if (isKorean) {
                val row1 = if (isShift) KO_ROW1S else KO_ROW1
                addView(buildRow(row1.map { it to 1f }))
                addView(buildRow(KO_ROW2.map { it to 1f }, sidePad = dp(18)))
                addView(
                    buildRow(
                        listOf("⇧" to 1.5f) +
                            KO_ROW3.map { it to 1f } +
                            listOf("⌫" to 1.5f),
                    ),
                )
            } else {
                val transform: (String) -> String = if (isShift) String::uppercase else String::lowercase
                addView(buildRow(EN_ROW1.map { transform(it) to 1f }))
                addView(buildRow(EN_ROW2.map { transform(it) to 1f }, sidePad = dp(18)))
                addView(
                    buildRow(
                        listOf("⇧" to 1.5f) +
                            EN_ROW3.map { transform(it) to 1f } +
                            listOf("⌫" to 1.5f),
                    ),
                )
            }
        }
    }

    private fun buildBottomRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply {
                    setMargins(0, 0, 0, dp(5))
                }
            addView(buildKey("한/EN", 1.5f, KeyType.SPECIAL).also { btnLang = it as TextView })
            addView(buildKey("SPACE", 5f, KeyType.NORMAL))
            addView(buildKey("확인", 1.5f, KeyType.CONFIRM))
        }
    }

    private fun buildRow(
        keys: List<Pair<String, Float>>,
        sidePad: Int = 0,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, dp(50)).apply {
                    setMargins(sidePad, 0, sidePad, dp(5))
                }
            keys.forEach { (label, weight) ->
                val type =
                    when (label) {
                        "⇧", "⌫" -> KeyType.SPECIAL
                        "확인" -> KeyType.CONFIRM
                        else -> KeyType.NORMAL
                    }
                addView(buildKey(label, weight, type))
            }
        }
    }

    private enum class KeyType { NORMAL, SPECIAL, CONFIRM }

    private fun buildKey(
        label: String,
        weight: Float,
        type: KeyType,
    ): View {
        val bgColor =
            when (type) {
                KeyType.CONFIRM -> Color.parseColor("#4A90D9")
                KeyType.SPECIAL -> Color.parseColor("#AEB2BA")
                KeyType.NORMAL -> Color.WHITE
            }
        val txtColor = if (type == KeyType.CONFIRM) Color.WHITE else Color.BLACK
        val display =
            when (label) {
                "SPACE" -> ""
                else -> label
            }
        val fontSize =
            when {
                label == "SPACE" -> 0f
                label.length == 1 -> 18f
                else -> 12f
            }

        return TextView(this).apply {
            text = display
            textSize = fontSize
            typeface = if (label.length == 1) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(txtColor)
            gravity = Gravity.CENTER
            background =
                GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = dp(5).toFloat()
                }
            elevation = dp(2).toFloat()
            layoutParams =
                LinearLayout.LayoutParams(0, MATCH_PARENT, weight).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            setOnClickListener { onKey(label) }
        }
    }

    private fun onKey(label: String) {
        when (label) {
            "⌫" -> handleBackspace()
            "⇧" -> toggleShift()
            "SPACE" -> handleSpace()
            "확인" -> confirm()
            "한/EN" -> toggleLang()
            else -> handleChar(label)
        }
    }

    private fun handleChar(label: String) {
        if (isKorean) {
            val jamo = label.first()
            val result = hangul.input(jamo)
            when (result) {
                is HangulComposer.Result.Compose -> {
                    if (result.commit.isNotEmpty()) {
                        currentInputConnection?.commitText(result.commit, 1)
                        currentText.append(result.commit)
                    }
                    currentInputConnection?.setComposingText(result.composing, 1)
                    refreshDisplay(result.composing)
                }
                else -> {}
            }
        } else {
            val ch = if (isShift) label.uppercase() else label.lowercase()
            currentInputConnection?.commitText(ch, 1)
            currentText.append(ch)
            refreshDisplay()
            if (isShift) {
                isShift = false
                rebuildKeys()
            }
        }
    }

    private fun handleBackspace() {
        if (isKorean) {
            val result = hangul.backspace()
            when (result) {
                is HangulComposer.Result.Backspace -> {
                    currentInputConnection?.setComposingText(result.composing, 1)
                    refreshDisplay(result.composing)
                }
                is HangulComposer.Result.DeleteChar -> {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    if (currentText.isNotEmpty()) currentText.deleteCharAt(currentText.length - 1)
                    refreshDisplay()
                }
                else -> {}
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
            if (currentText.isNotEmpty()) currentText.deleteCharAt(currentText.length - 1)
            refreshDisplay()
        }
    }

    private fun handleSpace() {
        if (isKorean && !hangul.isEmpty()) {
            val committed = hangul.flush()
            currentInputConnection?.commitText(committed, 1)
            currentText.append(committed)
        }
        currentInputConnection?.commitText(" ", 1)
        currentText.append(" ")
        refreshDisplay()
    }

    private fun confirm() {
        Log.d(TAG, "confirm() called — currentText='$currentText' hangul.isEmpty=${hangul.isEmpty()}")
        if (isKorean && !hangul.isEmpty()) {
            val last = hangul.flush()
            Log.d(TAG, "confirm: flushing hangul='$last'")
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        val text = currentText.toString().trim()
        Log.d(TAG, "confirm: final text='$text' (length=${text.length})")
        if (text.isNotEmpty()) triggerBanner(text)
        currentText.clear()
        hangul.reset()
        refreshDisplay()
        sendDefaultEditorAction(true)
    }

    private fun toggleShift() {
        isShift = !isShift
        rebuildKeys()
    }

    private fun toggleLang() {
        if (isKorean && !hangul.isEmpty()) {
            val last = hangul.flush()
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        isKorean = !isKorean
        hangul.reset()
        rebuildKeys()
    }

    private fun rebuildKeys() {
        keyContainer.removeAllViews()
        keyContainer.addView(buildLetterRows())
        keyContainer.addView(buildBottomRow())
    }

    private fun triggerBanner(text: String) {
        Log.d(TAG, "triggerBanner called: text='$text'")
        val food = KeyboardFoodCache.findExact(text) ?: KeyboardFoodCache.findContained(text)
        Log.d(TAG, "triggerBanner matched: ${food?.name ?: "NONE"} (grade=${food?.grade ?: "-"})")
        if (food == null) {
            hideInlineBanner()
            return
        }
        val shortText = gradeToShortMessage(food.displayName ?: food.name, food.grade)
        updateInlineBanner(shortText, gradeColor(food.grade))
    }

    private fun gradeToShortMessage(
        food: String,
        grade: String?,
    ): String =
        when (grade) {
            "S" -> "$food(S) 잘 맞아요!"
            "A" -> "$food(A) 좋은 선택!"
            "B" -> "$food(B) 무난해요!"
            "C" -> "$food(C) 양 조심!"
            "D" -> "$food(D) 혈당 주의!"
            else -> "$food 기록이 없어요!"
        }

    private fun gradeColor(grade: String?): Int =
        when (grade) {
            "S" -> Color.parseColor("#4DBA87")
            "A" -> Color.parseColor("#7BCAA0")
            "B" -> Color.parseColor("#F9CD7E")
            "C" -> Color.parseColor("#F6B44C")
            "D" -> Color.parseColor("#E96A6A")
            else -> Color.parseColor("#999999")
        }

    companion object {
        private const val TAG = "GlucoseKeyboard"
    }

    private fun refreshDisplay(composing: String = "") {
        val display = currentText.toString() + composing
        triggerBanner(display)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentText.clear()
        hangul.reset()
        refreshDisplay()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        val ic = currentInputConnection ?: return
        // 필드 클리어(=send) 감지: 새 selection이 (0,0)이고 직전엔 내용 있었음.
        if (newSelStart == 0 && newSelEnd == 0 && oldSelEnd > 0) {
            val pending = lastEditorText.trim()
            Log.d(TAG, "onUpdateSelection: send detected, lastEditorText='$pending'")
            if (pending.length >= 2) triggerBanner(pending)
            lastEditorText = ""
            currentText.clear()
            hangul.reset()
            refreshDisplay()
            return
        }
        // 그 외엔 실제 EditText 스냅샷 갱신. send 시점에 사용.
        val before = ic.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(256, 0)?.toString().orEmpty()
        val snapshot = (before + after).trim()
        if (snapshot.isNotEmpty()) {
            lastEditorText = snapshot
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerManager.dismiss()
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
