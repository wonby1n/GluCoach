package com.ssafy.s309.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import com.ssafy.s309.MainActivity
import com.ssafy.s309.R
import com.ssafy.s309.data.ble.BleManager
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.projector.ProjectorSocketClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE 혈당 스트림을 감시해 이상 패턴 감지 시 OS 알림을 발생시키고,
 * 인앱 알림 패널용 스트림도 emit 한다.
 *
 * [alertLow] / [alertHigh] 는 사용자 설정 로드 후 [updateThresholds] 로 갱신한다.
 * 기본값은 저혈당 70 mg/dL, 고혈당 180 mg/dL.
 */
@Singleton
class GlucoseAlertManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val bleManager: BleManager,
        private val projectorClient: ProjectorSocketClient,
        private val ttsManager: TtsManager,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val _alertStream = MutableSharedFlow<NotificationItem>(replay = 0, extraBufferCapacity = 16)
        val alertStream: SharedFlow<NotificationItem> = _alertStream.asSharedFlow()

        private val lastAlertMs = mutableMapOf<AlertType, Long>()
        private val recentReadings = ArrayDeque<GlucoseReading>()
        private val notifIdCounter = AtomicInteger(2000)

        @Volatile var alertLow: Int = DEFAULT_ALERT_LOW

        @Volatile var alertHigh: Int = DEFAULT_ALERT_HIGH

        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            createChannels()
            scope.launch {
                bleManager.glucoseReadings.collect { reading ->
                    processReading(reading)
                }
            }
        }

        fun updateThresholds(
            low: Int,
            high: Int,
        ) {
            alertLow = low
            alertHigh = high
        }

        private fun createChannels() {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, "혈당 긴급 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "저혈당·고혈당 긴급 경보" },
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_COACH, "AI 코치 메시지", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "GluCoach AI의 맞춤 건강 조언" },
            )
        }

        private fun processReading(reading: GlucoseReading) {
            synchronized(recentReadings) {
                recentReadings.addLast(reading)
                if (recentReadings.size > 12) recentReadings.removeFirst()
            }

            val value = reading.valueMgDl
            val now = System.currentTimeMillis()

            when {
                value <= alertLow -> {
                    if (canAlert(AlertType.LOW, now)) {
                        val sound =
                            when {
                                value < 55 -> R.raw.alert_very_low
                                value < 65 -> R.raw.alert_low
                                else -> R.raw.alert_borderline
                            }
                        fire(AlertType.LOW, "저혈당 위험 ⚠️", buildLowMessage(value), CHANNEL_ALERT, now, sound)
                    }
                }
                value >= alertHigh -> {
                    if (canAlert(AlertType.HIGH, now)) {
                        val sound =
                            when {
                                value > 250 -> R.raw.alert_very_high
                                value > 200 -> R.raw.alert_high
                                else -> R.raw.alert_slightly_high
                            }
                        fire(AlertType.HIGH, "혈당이 높아요", buildHighMessage(value), CHANNEL_COACH, now, sound)
                    }
                }
                else -> {
                    val delta = trendDelta()
                    when {
                        delta >= RAPID_RISE_THRESHOLD && canAlert(AlertType.RISING, now) ->
                            fire(
                                AlertType.RISING,
                                "혈당 빠르게 상승 중",
                                "혈당이 빠르게 오르고 있어요 (+${delta}mg/dL). 방금 드신 음식의 영향인 것 같아요. 물 한 잔 드시면 도움이 돼요 💧",
                                CHANNEL_COACH,
                                now,
                                R.raw.alert_rising,
                            )
                        delta <= -RAPID_FALL_THRESHOLD && canAlert(AlertType.FALLING, now) ->
                            fire(
                                AlertType.FALLING,
                                "혈당 빠르게 하강 중",
                                "혈당이 빠르게 내려가고 있어요 (${delta}mg/dL). 저혈당 예방을 위해 간식을 준비해두세요 🍪",
                                CHANNEL_COACH,
                                now,
                                R.raw.alert_falling,
                            )
                    }
                }
            }
        }

        /** 최근 3개 reading 간 변화량 (양수 = 상승). */
        private fun trendDelta(): Int {
            val readings = synchronized(recentReadings) { recentReadings.toList() }
            if (readings.size < 3) return 0
            val recent = readings.takeLast(3)
            return recent.last().valueMgDl - recent.first().valueMgDl
        }

        private fun canAlert(
            type: AlertType,
            now: Long,
        ): Boolean {
            val cooldown = if (type == AlertType.LOW) COOLDOWN_URGENT_MS else COOLDOWN_NORMAL_MS
            return now - (lastAlertMs[type] ?: 0L) >= cooldown
        }

        private fun fire(
            type: AlertType,
            title: String,
            message: String,
            channel: String,
            now: Long,
            @RawRes soundResId: Int,
        ) {
            lastAlertMs[type] = now
            val id = notifIdCounter.incrementAndGet()
            val urgent = type == AlertType.LOW

            if (type == AlertType.HIGH || type == AlertType.LOW) {
                scope.launch { projectorClient.alert() }
            }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                )

            notificationManager.notify(
                id,
                NotificationCompat.Builder(context, channel)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build(),
            )

            ttsManager.playSound(soundResId, urgent)

            _alertStream.tryEmit(
                NotificationItem(
                    id = id.toLong(),
                    title = title,
                    message = message,
                    timeAgoText = "방금",
                    isUnread = true,
                ),
            )
        }

        private fun buildLowMessage(value: Int): String =
            when {
                value < 55 -> "혈당이 ${value}mg/dL로 매우 위험해요! 지금 바로 당분을 드세요! 주스 150ml 또는 사탕 3개 🚨"
                value < 65 -> "혈당이 ${value}mg/dL로 낮아요. 빠르게 당분을 섭취하세요. 주스나 사탕이 도움이 돼요 🍊"
                else -> "혈당이 ${value}mg/dL로 경계선이에요. 간식을 드시거나 당분을 준비해두세요 🍪"
            }

        private fun buildHighMessage(value: Int): String =
            when {
                value > 250 -> "혈당이 ${value}mg/dL로 많이 높아요. 물을 충분히 드시고 인슐린 용량을 확인해보세요 💊"
                value > 200 -> "혈당이 ${value}mg/dL까지 올랐어요. 가벼운 20분 산책이 도움이 돼요 🚶"
                else -> "혈당이 ${value}mg/dL이에요. 오늘 식사 내용을 기록해두면 패턴 파악에 좋아요 📝"
            }

        /** FCM 서버 메시지를 인앱 알림 패널 스트림에만 emit. TTS는 FcmService가 담당. */
        fun emitFcmAlert(
            title: String,
            body: String,
        ) {
            _alertStream.tryEmit(
                NotificationItem(
                    id = notifIdCounter.incrementAndGet().toLong(),
                    title = title,
                    message = body,
                    timeAgoText = "방금",
                    isUnread = true,
                ),
            )
        }

        enum class AlertType { LOW, HIGH, RISING, FALLING }

        companion object {
            const val CHANNEL_ALERT = "glucoach_alert"
            const val CHANNEL_COACH = "glucoach_coach"

            private const val DEFAULT_ALERT_LOW = 70
            private const val DEFAULT_ALERT_HIGH = 180

            private const val COOLDOWN_URGENT_MS = 10 * 60 * 1000L
            private const val COOLDOWN_NORMAL_MS = 15 * 60 * 1000L

            private const val RAPID_RISE_THRESHOLD = 30
            private const val RAPID_FALL_THRESHOLD = 25
        }
    }
