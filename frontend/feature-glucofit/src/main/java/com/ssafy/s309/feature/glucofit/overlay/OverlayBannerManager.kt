package com.ssafy.s309.feature.glucofit.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ssafy.s309.feature.glucofit.R
import com.ssafy.s309.feature.glucofit.data.FoodInfo

class OverlayBannerManager(private val context: Context) {
    private var bannerView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show(
        food: FoodInfo,
        glucose: Double?,
    ) {
        showWithMessage(buildMessage(food, glucose))
    }

    /** B-2 정책 — KeyboardMessageBuilder 결과를 그대로 출력. */
    fun showWithMessage(
        text: String,
        color: Int,
    ) {
        showWithMessage(text to color)
    }

    private fun showWithMessage(textColor: Pair<String, Int>) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("OverlayBannerManager", "오버레이 권한 없음 — SYSTEM_ALERT_WINDOW 권한을 허용해야 배너가 표시됩니다")
            return
        }
        dismiss()

        val banner = buildBannerView(textColor.first, textColor.second)

        val screenWidth = context.resources.displayMetrics.widthPixels
        val params =
            WindowManager.LayoutParams(
                (screenWidth * 0.4f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                y = dp(48)
                x = dp(12)
            }

        bannerView = banner
        wm.addView(banner, params)
        handler.postDelayed({ dismiss() }, 2000)
    }

    private fun buildBannerView(
        message: String,
        textColor: Int,
    ): LinearLayout {
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background =
                    GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), Color.parseColor("#E0E0E0"))
                    }
                elevation = dp(8).toFloat()
            }

        container.addView(
            ImageView(context).apply {
                setImageResource(R.drawable.character)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams =
                    LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                        setMargins(0, 0, dp(10), 0)
                    }
            },
        )

        container.addView(
            TextView(context).apply {
                text = message
                textSize = 18f
                setTextColor(textColor)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            },
        )

        container.setOnClickListener { dismiss() }
        return container
    }

    private fun buildMessage(
        food: FoodInfo,
        glucose: Double?,
    ): Pair<String, Int> {
        val name = food.name
        val gi = food.giLevel

        if (glucose == null) {
            return "$name, 혈당 측정 중이 아니에요" to Color.parseColor("#9E9E9E")
        }

        val g = glucose.toInt()
        return when {
            glucose > 180 ->
                when (gi) {
                    "높음" -> "혈당 $g! ${name}는 지금 피하세요" to Color.parseColor("#E53935")
                    "중간" -> "혈당이 높아요($g). $name 양을 줄이세요" to Color.parseColor("#FF6F00")
                    else -> "혈당 ${g}이지만 ${name}는 그나마 괜찮아요" to Color.parseColor("#FF6F00")
                }
            glucose < 70 ->
                when (gi) {
                    "높음" -> "저혈당($g)! ${name}로 빠르게 올리세요" to Color.parseColor("#1E88E5")
                    "중간" -> "혈당 낮아요($g). $name 후 다시 확인하세요" to Color.parseColor("#1E88E5")
                    else -> "저혈당인데 ${name}는 흡수가 느려요" to Color.parseColor("#1E88E5")
                }
            else ->
                when (gi) {
                    "높음" -> "${name}는 혈당을 빠르게 올릴 수 있어요" to Color.parseColor("#FB8C00")
                    "중간" -> "$name, 적당히 드세요!" to Color.parseColor("#71C1D2")
                    else -> "${name}는 혈당에 좋은 선택이에요" to Color.parseColor("#43A047")
                }
        }
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        bannerView?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
            bannerView = null
        }
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
}
