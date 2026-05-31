package com.ssafy.s309

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ssafy.s309.data.repository.KeyboardFoodSyncManager
import com.ssafy.s309.notification.GlucoseAlertManager
import com.ssafy.s309.notification.MealReminderScheduler
import com.ssafy.s309.notification.TtsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class S309Application : Application(), Configuration.Provider {
    // 주입만으로 싱글톤 초기화 → BLE 스트림 구독 즉시 시작
    @Inject lateinit var glucoseAlertManager: GlucoseAlertManager

    // TTS 엔진을 앱 시작 시 미리 초기화 (첫 알림에서 지연 없음)
    @Inject lateinit var ttsManager: TtsManager

    // IME 키보드용 음식 목록 동기화 (TTL 24h, 로그인 시에만)
    @Inject lateinit var keyboardFoodSync: KeyboardFoodSyncManager

    // 식사 알림 스케줄러 (아침 08:00 / 점심 12:30 / 저녁 18:30)
    @Inject lateinit var mealReminderScheduler: MealReminderScheduler

    // HiltWorker(GlucoseWidgetUpdateWorker) 가 Hilt 주입을 받을 수 있도록
    // 자체 WorkerFactory 를 WorkManager 에 주입한다.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        keyboardFoodSync.syncIfNeeded()
        mealReminderScheduler.scheduleAll()
    }
}
