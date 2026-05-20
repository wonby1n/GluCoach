package com.ssafy.s309.feature.glucofit.keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ssafy.s309.feature.glucofit.data.KeyboardFoodCache
import com.ssafy.s309.feature.glucofit.glucose.GlucoseSimulator
import com.ssafy.s309.feature.glucofit.overlay.OverlayBannerManager

class GlucoseKeyboard : InputMethodService() {
    private val currentText = StringBuilder()

    /** EditText 실제 내용 스냅샷. send 직전 마지막 비어있지 않은 값으로 갱신. */
    private var lastEditorText: String = ""
    private lateinit var keyContainer: LinearLayout
    private lateinit var btnLang: TextView
    private lateinit var btnSym: TextView
    private lateinit var inlineBanner: View
    private lateinit var inlineBannerDot: View
    private lateinit var inlineBannerText: TextView
    private lateinit var inlinePredictButton: View
    private val bannerManager by lazy { OverlayBannerManager(applicationContext) }
    private val hangul = HangulComposer()

    /** 기호 모드 진입 전 언어 모드 — ← 키로 복귀할 때 사용. */
    private var prevLangMode = KeyboardMode.KOREAN

    /** 현재 입력 필드의 EditorInfo. 확인 키가 SEND/GO/DONE 액션을 보낼지 Enter를 보낼지 결정에 사용. */
    private var currentEditorInfo: EditorInfo? = null

    /** 백스페이스 롱프레스 반복 삭제 (50 → 30 → 20ms 가속). */
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceStartTime = 0L
    private val backspaceRepeat =
        object : Runnable {
            override fun run() {
                handleBackspace()
                val elapsed = System.currentTimeMillis() - backspaceStartTime
                val delay =
                    when {
                        elapsed > 2000L -> 20L
                        elapsed > 1000L -> 30L
                        else -> 50L
                    }
                backspaceHandler.postDelayed(this, delay)
            }
        }

    /**
     * 배너 업데이트 디바운스 핸들러 (150ms).
     * 매 키 입력마다 음식 검색을 실행하지 않고 입력이 잠시 멈출 때만 실행.
     */
    private val bannerHandler = Handler(Looper.getMainLooper())

    /** 배너에 현재 표시 중인 매칭 음식. 배너 클릭 시 앱으로 전달. null이면 매칭 없음. */
    private var currentMatchedFood: com.ssafy.s309.feature.glucofit.data.KeyboardFoodItem? = null

    // ── Caps Lock & 더블-탭 Shift ──────────────────────────────────────────
    private var isCapsLock = false
    private val doubleTapHandler = Handler(Looper.getMainLooper())
    private var doubleTapPending = false

    // ── 키 미리보기 팝업 (재사용) ──────────────────────────────────────────
    private var keyPreviewPopup: PopupWindow? = null
    private var keyPreviewText: TextView? = null

    // ── Shift 토글 시 레이아웃 in-place 업데이트용 ──────────────────────────
    private var letterRowsContainer: LinearLayout? = null

    // ── 세션 영속화 ─────────────────────────────────────────────────────────
    private lateinit var prefs: SharedPreferences

    // ── 스페이스 롱프레스 커서 이동 ─────────────────────────────────────────
    private val spaceHandler = Handler(Looper.getMainLooper())
    private var spaceCursorMode = false
    private var spaceTouchDownX = 0f
    private var spaceStepsAccum = 0

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("glucose_keyboard_prefs", MODE_PRIVATE)
        mode =
            when (prefs.getString("mode", "KOREAN")) {
                "ENGLISH" -> KeyboardMode.ENGLISH
                "SYMBOLS" -> KeyboardMode.SYMBOLS
                else -> KeyboardMode.KOREAN
            }
        prevLangMode =
            when (prefs.getString("prevLangMode", "KOREAN")) {
                "ENGLISH" -> KeyboardMode.ENGLISH
                else -> KeyboardMode.KOREAN
            }
        KeyboardFoodCache.load(applicationContext)
        GlucoseSimulator.start(applicationContext)
    }

    /**
     * 입력 세션 시작마다 캐시 mtime 체크. KeyboardFoodSyncManager가 로그인 후 파일을 새로 쓰면
     * 그 다음 입력창 탭 시점에 자동 재로딩되어 stale grade=null 트랩을 회피한다.
     */
    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        currentEditorInfo = attribute
        KeyboardFoodCache.load(applicationContext)
    }

    private val KO_ROW1 = listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ")
    private val KO_ROW1S = listOf("ㅃ", "ㅉ", "ㄸ", "ㄲ", "ㅆ", "ㅛ", "ㅕ", "ㅑ", "ㅒ", "ㅖ")
    private val KO_ROW2 = listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ")
    private val KO_ROW3 = listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")

    private val EN_ROW1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val EN_ROW2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val EN_ROW3 = listOf("z", "x", "c", "v", "b", "n", "m")

    private val SYM_ROW1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val SYM_ROW2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
    private val SYM_ROW3 = listOf("!", "?", ".", ",", ":", ";", "'", "\"")

    private enum class KeyboardMode { KOREAN, ENGLISH, SYMBOLS }

    private var mode = KeyboardMode.KOREAN
    private var isShift = false

    override fun onCreateInputView(): View {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
        keyPreviewText = null
        letterRowsContainer = null
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#D1D5DB"))
                addView(buildInlineBanner().also { inlineBanner = it })
                addView(buildKeyboard().also { keyContainer = it })
            }
        // 내비게이션 바 높이에 맞게 하단 패딩을 동적으로 조정.
        root.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    ViewCompat.setOnApplyWindowInsetsListener(keyContainer) { _, insets ->
                        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        keyContainer.setPadding(dp(4), dp(8), dp(4), navBottom.coerceAtLeast(dp(8)))
                        insets
                    }
                    ViewCompat.requestApplyInsets(root)
                }

                override fun onViewDetachedFromWindow(v: View) {}
            },
        )
        return root
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
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
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
                    currentMatchedFood?.let {
                        putExtra("food_name", it.displayName ?: it.name)
                    }
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            startActivity(intent)
        }
        outer.addView(container)

        val predictButton =
            TextView(this).apply {
                text = "혈당 예측을 확인해보시겠어요? ›"
                textSize = 13f
                setTextColor(Color.parseColor("#4EA8BC"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), Color.parseColor("#4EA8BC"))
                    }
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        setMargins(0, dp(6), 0, 0)
                    }
                setOnClickListener {
                    val intent =
                        Intent().apply {
                            setClassName("com.ssafy.s309", "com.ssafy.s309.MainActivity")
                            putExtra("navigate_to", "glucose_predict")
                            currentMatchedFood?.let {
                                putExtra("food_name", it.displayName ?: it.name)
                            }
                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    startActivity(intent)
                }
            }.also { inlinePredictButton = it }
        outer.addView(predictButton)

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
        if (::inlinePredictButton.isInitialized) {
            inlinePredictButton.visibility = View.VISIBLE
        }
    }

    private fun hideInlineBanner() {
        if (::inlineBanner.isInitialized) inlineBanner.visibility = View.GONE
    }

    private fun buildKeyboard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8)) // 하단: onCreateInputView WindowInsets 콜백이 덮어씀
            addView(buildLetterRows())
            addView(buildBottomRow())
        }
    }

    private fun buildLetterRows(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            when (mode) {
                KeyboardMode.KOREAN -> {
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
                }
                KeyboardMode.ENGLISH -> {
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
                KeyboardMode.SYMBOLS -> {
                    addView(buildRow(SYM_ROW1.map { it to 1f }))
                    addView(buildRow(SYM_ROW2.map { it to 1f }))
                    addView(
                        buildRow(
                            listOf("⇧" to 1.5f) +
                                SYM_ROW3.map { it to 1f } +
                                listOf("⌫" to 1.5f),
                        ),
                    )
                }
            }
        }.also { letterRowsContainer = it }
    }

    /**
     * 하단 행: [한/EN] [123/←] [SPACE] [확인]
     *
     * btnLang: KOREAN↔ENGLISH 토글. SYMBOLS에서 누르면 KOREAN.
     * btnSym : SYMBOLS 전용 토글. SYMBOLS 진입/복귀.
     */
    private fun buildBottomRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply {
                    setMargins(0, 0, 0, dp(4))
                }
            addView(
                buildKey(langLabel(), 1.2f, KeyType.SPECIAL).also { v ->
                    // buildKey가 설정한 터치 리스너를 lang 전용으로 덮어씀
                    v.setOnTouchListener { view, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            toggleLang()
                        }
                        true
                    }
                    btnLang = v as TextView
                },
            )
            addView(
                buildKey(symLabel(), 1.2f, KeyType.SPECIAL).also { v ->
                    v.setOnTouchListener { view, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            toggleSym()
                        }
                        true
                    }
                    btnSym = v as TextView
                },
            )
            addView(buildSpaceKey())
            addView(buildKey("확인", 1.5f, KeyType.CONFIRM))
        }
    }

    /** 언어 토글 버튼 레이블 — 누르면 전환될 방향을 표시. */
    private fun langLabel(): String = if (mode == KeyboardMode.KOREAN) "EN" else "한"

    /** 기호 토글 버튼 레이블 — SYMBOLS일 때 ← (이전 언어 복귀), 아니면 123. */
    private fun symLabel(): String = if (mode == KeyboardMode.SYMBOLS) "←" else "123"

    /**
     * 스페이스 키: 일반 탭→공백, 롱프레스+드래그→커서 이동 (30dp = 1칸).
     * 자체 터치 핸들러로 처리하므로 setOnClickListener 불필요.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildSpaceKey(): View {
        return buildKey("SPACE", 4f, KeyType.NORMAL).apply {
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        spaceTouchDownX = event.rawX
                        spaceStepsAccum = 0
                        spaceCursorMode = false
                        spaceHandler.postDelayed({
                            spaceCursorMode = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (spaceCursorMode) {
                            val deltaX = event.rawX - spaceTouchDownX
                            val stepPx = dp(30).toFloat()
                            val newSteps = (deltaX / stepPx).toInt()
                            val diff = newSteps - spaceStepsAccum
                            if (diff != 0) {
                                val keyCode = if (diff > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                                repeat(kotlin.math.abs(diff)) {
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                                }
                                spaceStepsAccum = newSteps
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        spaceHandler.removeCallbacksAndMessages(null)
                        val wasCursorMode = spaceCursorMode
                        spaceCursorMode = false
                        if (!wasCursorMode) {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            handleSpace()
                        }
                    }
                    else -> {}
                }
                true
            }
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
                LinearLayout.LayoutParams(MATCH_PARENT, dp(52)).apply {
                    setMargins(sidePad, 0, sidePad, dp(4))
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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildKey(
        label: String,
        weight: Float,
        type: KeyType,
    ): View {
        // 기호 모드에서 ⇧는 동작하지 않으므로 시각적으로 비활성 표시.
        val shiftDisabled = label == "⇧" && mode == KeyboardMode.SYMBOLS
        val shiftOn = label == "⇧" && isShift && !shiftDisabled
        val bgColor =
            when (type) {
                KeyType.CONFIRM -> Color.parseColor("#4A90D9")
                KeyType.SPECIAL ->
                    when {
                        shiftDisabled -> Color.parseColor("#C2C5CC")
                        label == "⇧" && isCapsLock -> Color.parseColor("#1E5BA8")
                        label == "⇧" && isShift -> Color.parseColor("#4A90D9")
                        else -> Color.parseColor("#9DA3AC")
                    }
                KeyType.NORMAL -> Color.WHITE
            }
        val txtColor =
            when {
                type == KeyType.CONFIRM -> Color.WHITE
                shiftDisabled -> Color.parseColor("#B8BBC2")
                shiftOn -> Color.WHITE
                else -> Color.parseColor("#1A1A1A")
            }
        val display =
            when (label) {
                "SPACE" -> ""
                else -> label
            }
        val fontSize =
            when {
                label == "SPACE" -> 0f
                label.length == 1 -> 18f
                else -> 13f
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
                    cornerRadius = dp(8).toFloat()
                }
            elevation = dp(1).toFloat()
            layoutParams =
                LinearLayout.LayoutParams(0, MATCH_PARENT, weight).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }

            if (label == "⌫") {
                // ACTION_DOWN 즉시 1회 삭제 + LongPressTimeout 후 반복 시작.
                // setOnLongClickListener 대신 Handler로 직접 구현해 ACTION_DOWN 즉시 발화.
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            backspaceStartTime = System.currentTimeMillis()
                            handleBackspace()
                            backspaceHandler.postDelayed(
                                backspaceRepeat,
                                ViewConfiguration.getLongPressTimeout().toLong(),
                            )
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            backspaceHandler.removeCallbacksAndMessages(null)
                        }
                    }
                    true
                }
            } else {
                tag = label
                val showPreview = type == KeyType.NORMAL && label != "SPACE" && mode != KeyboardMode.SYMBOLS
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (!shiftDisabled) {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val currentLabel = v.tag as? String ?: label
                                val currentDisplay = if (currentLabel == "SPACE") "" else currentLabel
                                if (showPreview) showKeyPreview(v, currentDisplay)
                                onKey(currentLabel)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (showPreview) dismissKeyPreview()
                        }
                    }
                    true
                }
            }
        }
    }

    private fun ensureKeyPreviewPopup() {
        if (keyPreviewText != null) return
        val size = dp(56)
        keyPreviewText =
            TextView(this).apply {
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1A1A1A"))
                gravity = Gravity.CENTER
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), Color.parseColor("#CCCCCC"))
                    }
            }
        keyPreviewPopup =
            PopupWindow(keyPreviewText, size, size, false).apply {
                isOutsideTouchable = false
                isTouchable = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    private fun showKeyPreview(
        anchorView: View,
        text: String,
    ) {
        if (text.isEmpty()) return
        ensureKeyPreviewPopup()
        val popup = keyPreviewPopup ?: return
        val tv = keyPreviewText ?: return
        tv.text = text
        val size = dp(56)
        val xOff = (anchorView.width - size) / 2
        val yOff = -(anchorView.height + size + dp(4))
        if (popup.isShowing) {
            popup.update(anchorView, xOff, yOff, size, size)
        } else {
            popup.showAsDropDown(anchorView, xOff, yOff)
        }
    }

    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
    }

    private fun onKey(label: String) {
        when (label) {
            "⌫" -> handleBackspace()
            "⇧" -> toggleShift()
            "SPACE" -> handleSpace() // buildSpaceKey 터치 핸들러가 우선. 여기는 fallback.
            "확인" -> confirm()
            else -> handleChar(label)
        }
    }

    private fun handleChar(label: String) {
        when (mode) {
            KeyboardMode.KOREAN -> {
                val jamo = label.first()
                val result = hangul.input(jamo)
                when (result) {
                    is HangulComposer.Result.Compose -> {
                        if (result.commit.isNotEmpty()) {
                            currentInputConnection?.commitText(result.commit, 1)
                            currentText.append(result.commit)
                        }
                        currentInputConnection?.setComposingText(result.composing, 1)
                        scheduleBanner(currentText.toString() + result.composing)
                    }
                    else -> {}
                }
                // 일반 Shift: 자모 하나 입력 후 자동 해제. Caps Lock: 해제하지 않음.
                if (isShift && !isCapsLock) {
                    isShift = false
                    doubleTapPending = false
                    doubleTapHandler.removeCallbacksAndMessages(null)
                    rebuildLetterRows()
                }
            }
            KeyboardMode.ENGLISH -> {
                val ch = if (isShift) label.uppercase() else label.lowercase()
                currentInputConnection?.commitText(ch, 1)
                currentText.append(ch)
                scheduleBanner(currentText.toString())
                // 일반 Shift: 한 글자 입력 후 자동 해제. Caps Lock: 해제하지 않음.
                if (isShift && !isCapsLock) {
                    isShift = false
                    doubleTapPending = false
                    doubleTapHandler.removeCallbacksAndMessages(null)
                    rebuildLetterRows()
                }
            }
            KeyboardMode.SYMBOLS -> {
                currentInputConnection?.commitText(label, 1)
                currentText.append(label)
                scheduleBanner(currentText.toString())
            }
        }
    }

    private fun handleBackspace() {
        if (mode == KeyboardMode.KOREAN) {
            // 백스페이스 전에 composing 상태를 기록.
            // HangulComposer.backspace()가 DeleteChar를 반환하는 경우 두 가지:
            //   (A) hadComposing=true  → 자음 단독(예: "ㄱ") composing을 지우는 것 → setComposingText("")로 지움
            //   (B) hadComposing=false → composer가 이미 비어있음 → committed 글자를 deleteSurroundingText로 지움
            val hadComposing = !hangul.isEmpty()
            val result = hangul.backspace()
            when (result) {
                is HangulComposer.Result.Backspace -> {
                    currentInputConnection?.setComposingText(result.composing, 1)
                    scheduleBanner(currentText.toString() + result.composing)
                }
                is HangulComposer.Result.DeleteChar -> {
                    if (hadComposing) {
                        // (A) composing 자음을 editor에서 제거. committed 글자는 건드리지 않음.
                        currentInputConnection?.setComposingText("", 1)
                    } else {
                        // (B) committed 글자 삭제.
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        if (currentText.isNotEmpty()) currentText.deleteCharAt(currentText.length - 1)
                    }
                    scheduleBanner(currentText.toString())
                }
                else -> {}
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
            if (currentText.isNotEmpty()) currentText.deleteCharAt(currentText.length - 1)
            scheduleBanner(currentText.toString())
        }
    }

    private fun handleSpace() {
        if (mode == KeyboardMode.KOREAN && !hangul.isEmpty()) {
            val committed = hangul.flush()
            currentInputConnection?.commitText(committed, 1)
            currentText.append(committed)
        }
        currentInputConnection?.commitText(" ", 1)
        currentText.append(" ")
        scheduleBanner(currentText.toString())
    }

    private fun confirm() {
        Log.d(TAG, "confirm() called — currentText='$currentText' hangul.isEmpty=${hangul.isEmpty()}")
        if (mode == KeyboardMode.KOREAN && !hangul.isEmpty()) {
            val last = hangul.flush()
            Log.d(TAG, "confirm: flushing hangul='$last'")
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        val text = currentText.toString().trim()
        Log.d(TAG, "confirm: final text='$text' (length=${text.length})")
        bannerHandler.removeCallbacksAndMessages(null) // 예약된 업데이트 취소
        currentMatchedFood = null
        hideInlineBanner()
        currentText.clear()
        hangul.reset()
        performSendAction()
    }

    /**
     * 입력 필드의 IME 액션(SEND/GO/DONE/SEARCH 등)이 설정돼 있으면 그걸 발행.
     * 그렇지 않은 경우(KakaoTalk 등 채팅창)는 ENTER 키 이벤트로 폴백.
     */
    private fun performSendAction() {
        val ei = currentEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val hasAction =
            action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED
        if (hasAction) {
            val handled = sendDefaultEditorAction(true)
            if (handled) return
        }
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    /**
     * Shift 상태 머신:
     *   Off → 탭 → Shift (더블탭 대기)
     *   Shift(대기중) → 탭 → Caps Lock
     *   Shift(대기 만료) → 탭 → Off
     *   Caps Lock → 탭 → Off
     */
    private fun toggleShift() {
        if (mode == KeyboardMode.SYMBOLS) return
        when {
            isCapsLock -> {
                isCapsLock = false
                isShift = false
                doubleTapHandler.removeCallbacksAndMessages(null)
                doubleTapPending = false
            }
            doubleTapPending -> {
                doubleTapHandler.removeCallbacksAndMessages(null)
                doubleTapPending = false
                isCapsLock = true
                isShift = true
            }
            isShift -> {
                isShift = false
                isCapsLock = false
            }
            else -> {
                isShift = true
                doubleTapPending = true
                doubleTapHandler.postDelayed({
                    doubleTapPending = false
                }, ViewConfiguration.getDoubleTapTimeout().toLong())
            }
        }
        rebuildLetterRows()
    }

    /**
     * 언어 토글: KOREAN↔ENGLISH 2-way. SYMBOLS에서 누르면 KOREAN으로.
     */
    private fun toggleLang() {
        if (mode == KeyboardMode.KOREAN && !hangul.isEmpty()) {
            val last = hangul.flush()
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        mode =
            when (mode) {
                KeyboardMode.KOREAN -> KeyboardMode.ENGLISH
                KeyboardMode.ENGLISH -> KeyboardMode.KOREAN
                KeyboardMode.SYMBOLS -> KeyboardMode.KOREAN
            }
        hangul.reset()
        isShift = false
        isCapsLock = false
        rebuildKeys()
    }

    /**
     * 기호 모드 토글: SYMBOLS 진입 또는 이전 언어 모드로 복귀.
     * prevLangMode에 진입 전 언어를 저장해 ← 로 정확히 돌아감.
     */
    private fun toggleSym() {
        if (mode == KeyboardMode.KOREAN && !hangul.isEmpty()) {
            val last = hangul.flush()
            currentInputConnection?.commitText(last, 1)
            currentText.append(last)
        }
        if (mode == KeyboardMode.SYMBOLS) {
            mode = prevLangMode
        } else {
            prevLangMode = mode
            mode = KeyboardMode.SYMBOLS
        }
        hangul.reset()
        isShift = false
        isCapsLock = false
        rebuildKeys()
    }

    /** shift 전용 — 기존 View를 재활용해 텍스트·색상만 업데이트. */
    private fun rebuildLetterRows() {
        if (letterRowsContainer != null && keyContainer.getChildAt(0) === letterRowsContainer) {
            updateLetterRowsInPlace()
        } else {
            if (keyContainer.childCount > 0) keyContainer.removeViewAt(0)
            keyContainer.addView(buildLetterRows(), 0)
        }
    }

    private fun updateLetterRowsInPlace() {
        val container = letterRowsContainer ?: return
        when (mode) {
            KeyboardMode.KOREAN -> {
                val row1Labels = if (isShift) KO_ROW1S else KO_ROW1
                val row1 = container.getChildAt(0) as? LinearLayout ?: return
                for (i in row1Labels.indices) {
                    val tv = row1.getChildAt(i) as? TextView ?: continue
                    tv.text = row1Labels[i]
                    tv.tag = row1Labels[i]
                }
                val row3 = container.getChildAt(2) as? LinearLayout ?: return
                (row3.getChildAt(0) as? TextView)?.let { updateShiftKeyView(it) }
            }
            KeyboardMode.ENGLISH -> {
                val transform: (String) -> String = if (isShift) String::uppercase else String::lowercase
                val row1 = container.getChildAt(0) as? LinearLayout ?: return
                for (i in EN_ROW1.indices) {
                    val tv = row1.getChildAt(i) as? TextView ?: continue
                    val label = transform(EN_ROW1[i])
                    tv.text = label
                    tv.tag = label
                }
                val row2 = container.getChildAt(1) as? LinearLayout ?: return
                for (i in EN_ROW2.indices) {
                    val tv = row2.getChildAt(i) as? TextView ?: continue
                    val label = transform(EN_ROW2[i])
                    tv.text = label
                    tv.tag = label
                }
                val row3 = container.getChildAt(2) as? LinearLayout ?: return
                for (i in EN_ROW3.indices) {
                    val tv = row3.getChildAt(i + 1) as? TextView ?: continue
                    val label = transform(EN_ROW3[i])
                    tv.text = label
                    tv.tag = label
                }
                (row3.getChildAt(0) as? TextView)?.let { updateShiftKeyView(it) }
            }
            KeyboardMode.SYMBOLS -> {}
        }
    }

    private fun updateShiftKeyView(tv: TextView) {
        val bgColor =
            when {
                isCapsLock -> Color.parseColor("#1E5BA8")
                isShift -> Color.parseColor("#4A90D9")
                else -> Color.parseColor("#9DA3AC")
            }
        val txtColor = if (isShift) Color.WHITE else Color.parseColor("#1A1A1A")
        (tv.background as? GradientDrawable)?.setColor(bgColor)
        tv.setTextColor(txtColor)
    }

    /** 모드 전환 — 전체 재빌드 (하단 행 레이블도 변경됨). */
    private fun rebuildKeys() {
        keyContainer.removeAllViews()
        keyContainer.addView(buildLetterRows())
        keyContainer.addView(buildBottomRow())
    }

    /**
     * 배너 갱신을 150ms 뒤로 미룸.
     * 연속 입력 중에는 검색을 건너뛰고 입력이 잠시 멈출 때만 실행해 성능 개선.
     * confirm()은 이 함수를 거치지 않고 triggerBanner()를 직접 호출.
     */
    private fun scheduleBanner(display: String) {
        bannerHandler.removeCallbacksAndMessages(null)
        bannerHandler.postDelayed({ triggerBanner(display.trim()) }, 150L)
    }

    private fun triggerBanner(text: String) {
        Log.d(TAG, "triggerBanner called: text='$text'")
        val food = KeyboardFoodCache.findBest(text)
        Log.d(TAG, "triggerBanner matched: ${food?.name ?: "NONE"} (grade=${food?.grade ?: "-"})")
        currentMatchedFood = food
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

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        prefs.edit()
            .putString("mode", mode.name)
            .putString("prevLangMode", prevLangMode.name)
            .apply()
        dismissKeyPreview()
        bannerHandler.removeCallbacksAndMessages(null)
        currentText.clear()
        hangul.reset()
        hideInlineBanner()
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
        // candidatesStart < 0 조건 추가: 한글 composing 삭제 시 selection이 (0,0)으로 돌아가는
        // 경우를 오탐하지 않도록 방어. (composing "ㄱ" → setComposingText("") 시 발생)
        if (newSelStart == 0 && newSelEnd == 0 && oldSelEnd > 0 && candidatesStart < 0) {
            Log.d(TAG, "onUpdateSelection: send detected — hiding banner")
            lastEditorText = ""
            currentText.clear()
            hangul.reset()
            bannerHandler.removeCallbacksAndMessages(null)
            currentMatchedFood = null
            hideInlineBanner()
            return
        }
        val before = ic.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(256, 0)?.toString().orEmpty()
        val snapshot = (before + after).trim()
        if (snapshot.isNotEmpty()) lastEditorText = snapshot
    }

    override fun onDestroy() {
        super.onDestroy()
        backspaceHandler.removeCallbacks(backspaceRepeat)
        spaceHandler.removeCallbacksAndMessages(null)
        doubleTapHandler.removeCallbacksAndMessages(null)
        dismissKeyPreview()
        keyPreviewPopup = null
        keyPreviewText = null
        letterRowsContainer = null
        bannerHandler.removeCallbacksAndMessages(null)
        bannerManager.dismiss()
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
