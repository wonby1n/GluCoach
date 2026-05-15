package com.ssafy.s309.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ssafy.s309.MainActivity
import com.ssafy.s309.R
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.UserRepository
import com.ssafy.s309.notification.GlucoseAlertManager
import com.ssafy.s309.notification.TtsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {
    @Inject lateinit var userRepository: UserRepository

    @Inject lateinit var tokenManager: TokenManager

    @Inject lateinit var ttsManager: TtsManager

    @Inject lateinit var glucoseAlertManager: GlucoseAlertManager

    @Inject lateinit var healthRepository: HealthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM 토큰 갱신: $token")
        tokenManager.saveFcmToken(token)

        // 로그인 상태이면 즉시 서버에 등록
        if (tokenManager.getUserId() != null) {
            scope.launch {
                userRepository.registerFcmToken(token)
                    .onFailure { Log.w(TAG, "FCM 토큰 서버 등록 실패", it) }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(
            TAG,
            "FCM 수신: alertType=${message.data["alertType"]}, " +
                "title=${message.notification?.title ?: message.data["title"]}, " +
                "data=${message.data}",
        )
        val title = message.notification?.title ?: message.data["title"] ?: "GluCoach"
        val body = message.notification?.body ?: message.data["body"] ?: return

        // data["alertType"] 우선, 없으면 title로 판별
        val alertType =
            message.data["alertType"]
                ?: if (title == MEAL_FOLLOWUP_TITLE) ALERT_TYPE_MEAL_FOLLOWUP else null

        // alertType별 전용 silent 채널 (사운드 없음 — TTS로 본문을 발화)
        val channelId = ensureChannelForAlertType(alertType)

        if (alertType?.startsWith(ALERT_TYPE_MEAL_FOLLOWUP) == true) {
            showMealFollowupNotification(title, body, channelId)
        } else {
            showNotification(title, body, channelId)
        }

        // 키키 메시지를 TTS로 발화
        ttsManager.speak(body)

        // 홈 대시보드(KikiSuggestionCard + 뱃지)용 — 모든 알림을 alertStream에 emit
        glucoseAlertManager.emitFcmAlert(title, body, alertType ?: "")

        // 채팅 메시지 FCM — KikiChatViewModel에 page=0 재조회 신호 전달
        if (message.data["chatMessageId"] != null) {
            healthRepository.emitChatFcmEvent()
        }
    }

    /**
     * alertType 별 silent 채널을 (없으면) 생성하고 채널 ID를 반환.
     * 채널 사운드는 [TtsManager] 의 본문 발화로 대체했으므로 [NotificationChannel.setSound] 를 사용하지 않는다.
     *
     * 채널 ID 승격 히스토리:
     *  v2 — mp3 가 setSound 로 박혀 있던 구버전
     *  v3 — silent 로 전환했으나 BE 가 notification payload 를 함께 보내 onMessageReceived 가 스킵돼
     *       단말 캐시 채널이 그대로 살아있는 사례가 발견됨
     *  v4 — BE 를 data-only payload 로 전환한 시점에 fresh 채널을 강제 생성하기 위한 승격
     */
    private fun ensureChannelForAlertType(alertType: String?): String {
        val channelId = channelIdForAlertType(alertType)
        val channelName = channelNameForAlertType(alertType)

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 잔존하는 legacy (v2: mp3 사운드 / v3: silent지만 onMessageReceived 미경유) 채널 정리.
        LEGACY_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }

        if (manager.getNotificationChannel(channelId) == null) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = channelName
                        setSound(null, null)
                        enableVibration(true)
                    }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    private fun channelIdForAlertType(alertType: String?): String =
        when {
            alertType == null -> CHANNEL_DEFAULT
            alertType.startsWith("AGENT_WAKE_UP") -> CHANNEL_WAKE_UP
            alertType.startsWith("AGENT_MEAL_FOLLOWUP") -> CHANNEL_MEAL_FOLLOWUP
            alertType.startsWith("AGENT_MEAL_REPLY") -> CHANNEL_MEAL_REPLY
            alertType.startsWith("AGENT_MEAL_RETRY") -> CHANNEL_MEAL_RETRY
            alertType.startsWith("AGENT_SLEEP_INSIGHT") -> CHANNEL_SLEEP_INSIGHT
            else -> CHANNEL_DEFAULT
        }

    private fun channelNameForAlertType(alertType: String?): String =
        when {
            alertType == null -> "키키 알림"
            alertType.startsWith("AGENT_WAKE_UP") -> "아침 브리핑"
            alertType.startsWith("AGENT_MEAL_FOLLOWUP") -> "식후 활동 제안"
            alertType.startsWith("AGENT_MEAL_REPLY") -> "키키 답변"
            alertType.startsWith("AGENT_MEAL_RETRY") -> "식후 재확인"
            alertType.startsWith("AGENT_SLEEP_INSIGHT") -> "수면 리포트"
            else -> "키키 알림"
        }

    private fun showMealFollowupNotification(
        title: String,
        body: String,
        channelId: String,
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 채널 생성/사운드 등록은 ensureChannelForAlertType에서 처리됨

        val notifId = System.currentTimeMillis().toInt()

        fun actionPendingIntent(
            reply: String,
            code: Int,
        ): PendingIntent {
            val intent =
                Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_MEAL_REPLY
                    putExtra(NotificationActionReceiver.EXTRA_USER_REPLY, reply)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
                }
            return PendingIntent.getBroadcast(
                this,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val openAppIntent =
            PendingIntent.getActivity(
                this,
                notifId,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.NAV_KIKI_ALARM_DETAIL)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        manager.notify(
            notifId,
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent)
                .addAction(0, "알겠어요", actionPendingIntent(NotificationActionReceiver.REPLY_OKAY, notifId + 1))
                .addAction(0, "회의 중", actionPendingIntent(NotificationActionReceiver.REPLY_BUSY, notifId + 2))
                .addAction(0, "괜찮아요", actionPendingIntent(NotificationActionReceiver.REPLY_DECLINE, notifId + 3))
                .build(),
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        channelId: String,
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // 채널 생성/사운드 등록은 ensureChannelForAlertType에서 처리됨

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.NAV_KIKI_ALARM_DETAIL)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    companion object {
        private const val TAG = "FcmService"

        // BE 를 data-only payload 로 전환하면서 *_v4 (silent) 로 한 단계 더 승격.
        private const val CHANNEL_DEFAULT = "kiki_default_v4"
        private const val CHANNEL_WAKE_UP = "kiki_wake_up_v4"
        private const val CHANNEL_MEAL_FOLLOWUP = "kiki_meal_followup_v4"
        private const val CHANNEL_MEAL_REPLY = "kiki_meal_reply_v4"
        private const val CHANNEL_MEAL_RETRY = "kiki_meal_retry_v4"
        private const val CHANNEL_SLEEP_INSIGHT = "kiki_sleep_insight_v4"

        // v2 (mp3 사운드) / v3 (silent 였지만 BE notification payload 경유로 단말 캐시가 살아남은 케이스) — 모두 정리.
        private val LEGACY_CHANNEL_IDS =
            listOf(
                "kiki_default_v2",
                "kiki_wake_up_v2",
                "kiki_meal_followup_v2",
                "kiki_meal_reply_v2",
                "kiki_meal_retry_v2",
                "kiki_sleep_insight_v2",
                "kiki_default_v3",
                "kiki_wake_up_v3",
                "kiki_meal_followup_v3",
                "kiki_meal_reply_v3",
                "kiki_meal_retry_v3",
                "kiki_sleep_insight_v3",
            )

        private const val ALERT_TYPE_MEAL_FOLLOWUP = "AGENT_MEAL_FOLLOWUP"
        private const val MEAL_FOLLOWUP_TITLE = "식후 컨디션"
    }
}
