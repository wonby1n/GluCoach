package com.ssafy.s309.feature.glucofit

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ssafy.s309.feature.glucofit.health.HealthConnectManager
import com.ssafy.s309.feature.glucofit.health.SamsungHealthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Samsung Health / Health Connect 동작 확인용 디버그 Activity.
 *
 * 런처에 노출되지 않으며 adb로만 실행:
 *   adb shell am start -n com.ssafy.s309/com.ssafy.s309.feature.glucofit.HealthDebugActivity
 *
 * 화면 진입 시 자동으로 Samsung Health 권한 요청 → 데이터 5종 로그 출력.
 * Health Connect 버튼은 Health Connect 앱 측 권한 다이얼로그를 띄움 (앱 미설치 기기에서는 동작 안 함).
 */
class HealthDebugActivity : Activity() {
    private val tag = "HealthDebugActivity"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var samsungHealth: SamsungHealthManager
    private lateinit var healthConnect: HealthConnectManager
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Samsung Health SDK는 API 29+ 필요. 그 이하 기기에서는 매니저 생성 자체가 실패할 수 있음.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setContentView(buildSimpleStatus("이 기기는 Samsung Health SDK 미지원 (API ${Build.VERSION.SDK_INT})"))
            return
        }

        samsungHealth = SamsungHealthManager(this)
        healthConnect = HealthConnectManager(this)

        setContentView(buildLayout())
        Log.i(tag, "HealthDebugActivity 생성됨 — Samsung Health 권한 요청 시작")
    }

    override fun onResume() {
        super.onResume()
        // Activity가 RESUMED 상태여야 권한 다이얼로그가 정상 표시됨
        if (::samsungHealth.isInitialized) {
            triggerSamsungHealthFlow()
        }
    }

    private fun triggerSamsungHealthFlow() {
        scope.launch {
            updateStatus("Samsung Health 권한 요청 중...")
            samsungHealth.requestPermissions()
            updateStatus("권한 다이얼로그 응답 대기 후 데이터 fetch")

            withContext(Dispatchers.IO) {
                Log.i(tag, "── Samsung Health 데이터 fetch 시작 ──")
                samsungHealth.getLatestBloodGlucose()
                samsungHealth.getTodayActiveCalories()
                samsungHealth.getTodayExerciseCalories()
                samsungHealth.getLastSleepDurationMinutes()
                samsungHealth.getLatestHeartRate()
                Log.i(tag, "── Samsung Health 데이터 fetch 완료 ──")
            }
            updateStatus("Samsung Health fetch 완료 (logcat 확인)")
        }
    }

    private fun triggerHealthConnectFlow() {
        scope.launch {
            if (!healthConnect.isAvailable()) {
                Log.w(tag, "Health Connect SDK 미사용 가능 — 앱 미설치 또는 미지원 기기")
                updateStatus("Health Connect 미사용 가능")
                return@launch
            }
            val granted = healthConnect.hasPermissions()
            Log.i(tag, "Health Connect 권한 부여 상태: $granted")
            if (granted) {
                val glucoses = healthConnect.readBloodGlucose()
                val nutrition = healthConnect.readNutrition()
                Log.i(tag, "혈당 ${glucoses.size}건, 영양 ${nutrition.size}건 조회됨")
                glucoses.take(5).forEach { Log.d(tag, "혈당: $it") }
                nutrition.take(5).forEach { Log.d(tag, "영양: $it") }
                updateStatus("Health Connect fetch 완료 (logcat 확인)")
            } else {
                Log.w(tag, "Health Connect 권한 미부여 — Health Connect 앱에서 수동 부여 필요")
                updateStatus("Health Connect 권한 미부여 (앱에서 직접 허용)")
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun buildLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)

            addView(
                TextView(context).apply {
                    text = "GlucoFit Health Debug"
                    textSize = 22f
                    setTextColor(Color.BLACK)
                },
            )

            addView(
                TextView(context).apply {
                    text = "Samsung Health은 진입 시 자동 실행됩니다.\nlogcat 확인: adb logcat -s SamsungHealthManager HealthDebugActivity HealthConnectManager"
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, dp(12), 0, dp(20))
                },
            )

            statusView =
                TextView(context).apply {
                    text = "초기화 중..."
                    textSize = 14f
                    setTextColor(Color.parseColor("#1976D2"))
                    setPadding(0, 0, 0, dp(20))
                }
            addView(statusView)

            addView(
                Button(context).apply {
                    text = "Samsung Health 다시 fetch"
                    setOnClickListener { triggerSamsungHealthFlow() }
                },
            )

            addView(
                Button(context).apply {
                    text = "Health Connect fetch"
                    setOnClickListener { triggerHealthConnectFlow() }
                },
            )
        }
    }

    private fun buildSimpleStatus(message: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(80), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
            addView(
                TextView(context).apply {
                    text = message
                    textSize = 16f
                    setTextColor(Color.BLACK)
                },
            )
        }
    }

    private fun updateStatus(message: String) {
        if (::statusView.isInitialized) statusView.text = message
        Log.i(tag, message)
    }

    private fun dp(v: Int) =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics,
        ).toInt()
}
