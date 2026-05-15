package com.ssafy.s309

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.messaging.FirebaseMessaging
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.UserRepository
import com.ssafy.s309.data.repository.source.SamsungHealthHolder
import com.ssafy.s309.navigation.AppNavigation
import com.ssafy.s309.ui.theme.S309Theme
import com.ssafy.s309.wear.WearDataSender
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Samsung Health SDK 는 Activity 인스턴스를 요구하므로, MainActivity 라이프사이클에
    // 맞춰 Holder 에 자기 자신을 등록한다. 미지원/미설치 디바이스에서는 Holder 가 내부적으로
    // null 을 유지하며 SamsungHealthDataSource 가 mock fallback 으로 떨어진다.
    //
    // @Inject lateinit var 대신 EntryPointAccessors 를 사용하는 이유: Hilt 2.55 + Kotlin 2.2
    // 조합에서 members-injection validation 시 dagger 의 kotlin-metadata-jvm 이 metadata
    // 버전 2.2.0 을 거부하는 이슈가 있다 (class file metadata 2.2 vs lib max 2.1). 필드 주입을
    // 피하면 해당 validation 경로를 우회한다. constructor injection 은 영향 없음.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MainActivityEntryPoint {
        fun samsungHealthHolder(): SamsungHealthHolder

        fun healthRepository(): HealthRepository

        fun tokenManager(): TokenManager

        fun userRepository(): UserRepository
    }

    private val samsungHealthHolder: SamsungHealthHolder by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, MainActivityEntryPoint::class.java)
            .samsungHealthHolder()
    }

    private val healthRepository: HealthRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, MainActivityEntryPoint::class.java)
            .healthRepository()
    }

    private val tokenManager: TokenManager by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, MainActivityEntryPoint::class.java)
            .tokenManager()
    }

    private val userRepository: UserRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, MainActivityEntryPoint::class.java)
            .userRepository()
    }

    // Samsung Health 권한 자동 요청은 Activity 라이프타임당 1회만. onResume 마다 다시 띄우면
    // 사용자가 한 번 거부 후 짜증나므로 가드. SDK 표준 동작상 이미 부여된 권한이면 다이얼로그
    // 자체가 안 뜨므로 추가 체크 불필요.
    private var samsungAutoRequested = false

    /** FCM 알림 탭 시 이동할 화면 경로 (null = 기본 동작) */
    private var pendingNavTarget by mutableStateOf<String?>(null)

    /** 키보드 배너 → 음식 성적표 진입 시 자동 열 음식명 (null = 메인 화면만) */
    private var pendingFoodName by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            Log.d("FCM", "알림 권한: $granted")
        }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            Log.d("VoiceQuery", "RECORD_AUDIO 권한: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        samsungHealthHolder.attach(this)
        startSamsungHealthPolling()
        requestNotificationPermission()
        requestRecordAudioPermission()
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "토큰 발급 실패", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            Log.d("FCM", "토큰: $token")
            // FcmService.onNewToken 은 토큰이 바뀔 때만 발화한다. 첫 부팅 콜백을 놓치거나
            // 등록 흐름이 깨졌던 시점에 발화했던 케이스를 복구하기 위해, 매 실행마다
            // 현재 토큰을 로컬에 저장하고 로그인 상태면 즉시 서버에 PUT 한다.
            tokenManager.saveFcmToken(token)
            if (tokenManager.getUserId() != null) {
                lifecycleScope.launch {
                    userRepository.registerFcmToken(token)
                        .onFailure { Log.w("FCM", "FCM 토큰 서버 등록 실패", it) }
                }
            }
        }
        pendingNavTarget = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        pendingFoodName = intent?.getStringExtra(EXTRA_FOOD_NAME)
        // enableEdgeToEdge()
        setContent {
            S309Theme {
                // 앱 전역 기본값: edge-to-edge 로 그려지는 상태바와 컨텐츠가 겹치지 않도록
                // 회원가입 온보딩과 동일하게 statusBarsPadding() 을 루트에 적용한다.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding(),
                ) {
                    AppNavigation(
                        pendingNavTarget = pendingNavTarget,
                        onNavTargetConsumed = { pendingNavTarget = null },
                        pendingFoodName = pendingFoodName,
                        onFoodNameConsumed = { pendingFoodName = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNavTarget = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        pendingFoodName = intent.getStringExtra(EXTRA_FOOD_NAME)
    }

    override fun onResume() {
        super.onResume()
        maybeRequestSamsungHealthAtLaunch()
    }

    override fun onDestroy() {
        samsungHealthHolder.detach()
        super.onDestroy()
    }

    /**
     * 앱 진입 직후 Samsung Health 권한 다이얼로그를 띄운다 (라이프타임 1회).
     *
     * - Activity 가 RESUMED 상태여야 다이얼로그가 표시되므로 onResume 에서 호출.
     * - SDK 표준 동작상 이미 부여된 권한이면 즉시 리턴되며 다이얼로그가 안 뜸.
     * - 매니저 null (SDK 미지원/미설치/생성 실패) 또는 API 29 미만이면 no-op.
     */
    private fun maybeRequestSamsungHealthAtLaunch() {
        if (samsungAutoRequested) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mgr = samsungHealthHolder.manager ?: return
        samsungAutoRequested = true
        lifecycleScope.launch {
            runCatching { mgr.requestPermissions() }
                .onFailure { Log.w("MainActivity", "Samsung Health 권한 요청 실패", it) }
        }
    }

    /**
     * Samsung Health 데이터를 1분마다 폴링한다 (걸음/활동칼로리/수면/혈당/심박).
     *
     * - foreground only: [repeatOnLifecycle] STARTED 게이트로 앱이 백그라운드 가면 자동 일시정지,
     *   다시 켜면 재개. WorkManager 없이 가벼운 디버깅/시연 용도.
     * - 매니저 null (SDK 미지원/미설치) 또는 권한 미부여 시 SDK 가 예외를 던지며,
     *   각 함수가 try/catch + 로그를 남기고 0/null 반환하므로 폴 루프가 죽지 않는다.
     * - 첫 실행은 즉시, 이후 60초 간격.
     */
    private fun startSamsungHealthPolling() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val mgr = samsungHealthHolder.manager
                    var realSteps: Long? = null
                    if (mgr == null) {
                        Log.d(POLL_TAG, "manager null — SDK 미지원/미부착, Samsung Health 스킵")
                    } else {
                        Log.i(POLL_TAG, "── Samsung Health poll 시작 ──")
                        runCatching {
                            realSteps = mgr.getTodaySteps()
                            val calories = mgr.getTodayActiveCalories()
                            val sleepMinutes = mgr.getLastSleepDurationMinutes()
                            mgr.getLatestBloodGlucose()
                            val heartRate = mgr.getLatestHeartRate()
                            mgr.getLatestSleepSession()?.let { s ->
                                healthRepository.syncSleepSession(
                                    startedAt = java.time.LocalDateTime.ofInstant(s.startTime, java.time.ZoneId.systemDefault()),
                                    endedAt = java.time.LocalDateTime.ofInstant(s.endTime, java.time.ZoneId.systemDefault()),
                                    source = "samsung_health",
                                )
                            }

                            // 1분 시계열 한 점 — health_snapshots (5개 모이면 batch INSERT)
                            healthRepository.bufferSnapshot(
                                com.ssafy.s309.data.model.HealthSnapshotItem(
                                    recordedAt =
                                        java.time.LocalDateTime.now().format(
                                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                        ),
                                    stepsTotal = realSteps?.toInt(),
                                    caloriesBurned = calories.takeIf { it > 0 }?.toDouble(),
                                    heartRate = heartRate?.toDouble(),
                                ),
                            )

                            // 일별 누적값 upsert — daily_health_summaries
                            healthRepository.upsertDailySummary(
                                date = java.time.LocalDate.now(),
                                steps = realSteps?.toInt(),
                                caloriesBurned = calories.takeIf { it > 0 }?.toDouble(),
                                sleepMinutes = sleepMinutes.takeIf { it > 0 },
                                avgHeartRate = heartRate?.toDouble(),
                            )
                        }.onFailure { Log.w(POLL_TAG, "poll 중 오류", it) }
                        Log.i(POLL_TAG, "── Samsung Health poll 완료 ──")
                    }

                    // 워치 송신: 진짜 걸음수 있으면 그 값, 없으면 1000~5999 사이 fake 값.
                    // 워치 ↔ 폰 통신 파이프 자체가 동작하는지 검증하기 위함이라 데이터 출처 무관하게 항상 송신.
                    val toSend =
                        realSteps?.toDouble() ?: run {
                            val fake = 1000.0 + ((System.currentTimeMillis() / 1000L) % 5000L)
                            Log.d(POLL_TAG, "걸음수 없음 — fake $fake 보 로 워치 송신 (파이프 검증)")
                            fake
                        }
                    WearDataSender.send(this@MainActivity, toSend)

                    delay(60_000L)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_FOOD_NAME = "food_name"
        const val NAV_KIKI_ALARM_DETAIL = "kiki_alarm_detail"
        const val NAV_FOOD_REPORT = "food_report"
        const val NAV_FOOD_SCAN = "food_scan"
        const val NAV_GLUCOSE_PREDICT = "glucose_predict"
        private const val POLL_TAG = "SHPoller"
    }
}
