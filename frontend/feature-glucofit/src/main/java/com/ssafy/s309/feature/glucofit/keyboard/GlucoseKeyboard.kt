package com.ssafy.s309.feature.glucofit.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import com.ssafy.s309.feature.glucofit.data.FoodDatabase
import com.ssafy.s309.feature.glucofit.glucose.GlucoseSimulator
import com.ssafy.s309.feature.glucofit.overlay.OverlayBannerManager

class GlucoseKeyboard : InputMethodService() {
    private val currentText = StringBuilder()
    private lateinit var tvDisplay: TextView
    private lateinit var keyContainer: LinearLayout
    private lateinit var btnLang: TextView
    private val bannerManager by lazy { OverlayBannerManager(applicationContext) }
    private val hangul = HangulComposer()
    private var isKorean = true

    private val foodChips =
        listOf(
            "마라탕", "치킨", "라면", "피자", "떡볶이",
            "초밥", "삼겹살", "짜장면", "냉면", "삼계탕",
        )

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
            addView(buildSuggestionStrip())
            addView(buildDisplayBar())
            addView(buildKeyboard().also { keyContainer = it })
        }
    }

    private fun buildSuggestionStrip(): View {
        val inner =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(5), dp(6), dp(5))
            }
        foodChips.forEach { food ->
            inner.addView(
                TextView(this).apply {
                    text = food
                    textSize = 14f
                    setTextColor(Color.parseColor("#222222"))
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    background =
                        GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(4).toFloat()
                            setStroke(dp(1), Color.parseColor("#D0D0D0"))
                        }
                    layoutParams =
                        LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                            setMargins(dp(3), 0, dp(3), 0)
                        }
                    setOnClickListener { tapChip(food) }
                },
            )
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#E4E5E8"))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(48))
            addView(inner)
        }
    }

    private fun buildDisplayBar(): TextView {
        return TextView(this).apply {
            setBackgroundColor(Color.parseColor("#F8F8F8"))
            setPadding(dp(16), 0, dp(16), 0)
            gravity = Gravity.CENTER_VERTICAL
            hint = "음식 이름 입력 또는 위 칩 선택"
            textSize = 15f
            setTextColor(Color.parseColor("#1A1A1A"))
            setHintTextColor(Color.parseColor("#BBBBBB"))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(40))
        }.also { tvDisplay = it }
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
        if (isKorean && !hangul.isEmpty()) {
            val last = hangul.flush()
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        val text = currentText.toString().trim()
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

    private fun tapChip(food: String) {
        if (isKorean && !hangul.isEmpty()) {
            currentInputConnection?.commitText(hangul.flush(), 1)
            hangul.reset()
        }
        currentInputConnection?.commitText(food, 1)
        currentText.clear()
        currentText.append(food)
        refreshDisplay()
        triggerBanner(food)
    }

    private fun rebuildKeys() {
        keyContainer.removeAllViews()
        keyContainer.addView(buildLetterRows())
        keyContainer.addView(buildBottomRow())
    }

    private fun triggerBanner(text: String) {
        val food = FoodDatabase.findFood(text) ?: return
        val glucose = GlucoseSimulator.glucoseState.value
        bannerManager.show(food, glucose)
    }

    private fun refreshDisplay(composing: String = "") {
        val display = currentText.toString() + composing
        tvDisplay.text = display
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentText.clear()
        hangul.reset()
        if (::tvDisplay.isInitialized) refreshDisplay()
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
