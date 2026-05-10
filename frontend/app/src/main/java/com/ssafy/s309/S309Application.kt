package com.ssafy.s309

import android.app.Application
import com.ssafy.s309.data.repository.KeyboardFoodSyncManager
import com.ssafy.s309.notification.GlucoseAlertManager
import com.ssafy.s309.notification.TtsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class S309Application : Application() {
    // 주입만으로 싱글톤 초기화 → BLE 스트림 구독 즉시 시작
    @Inject lateinit var glucoseAlertManager: GlucoseAlertManager

    // TTS 엔진을 앱 시작 시 미리 초기화 (첫 알림에서 지연 없음)
    @Inject lateinit var ttsManager: TtsManager

    // IME 키보드용 음식 목록 동기화 (TTL 24h, 로그인 시에만)
    @Inject lateinit var keyboardFoodSync: KeyboardFoodSyncManager

    override fun onCreate() {
        super.onCreate()
        keyboardFoodSync.syncIfNeeded()
    }
}
