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
import com.ssafy.s309.notification.NotificationIds
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
        val title = message.notification?.title ?: message.data["title"] ?: "GluCoach"
        val body = message.notification?.body ?: message.data["body"] ?: return

        // data["alertType"] 우선, 없으면 title로 판별
        val alertType =
            message.data["alertType"]
                ?: if (title == MEAL_FOLLOWUP_TITLE) ALERT_TYPE_MEAL_FOLLOWUP else null

        if (alertType?.startsWith(ALERT_TYPE_MEAL_FOLLOWUP) == true) {
            pushNotification(
                title = title,
                body = body,
                channelId = CHANNEL_COACHING,
                channelName = "키키 코칭 알림",
                channelDescription = "식후 활동 유도 및 선택 응답 알림",
                actionsBuilder = { notifId ->
                    listOf(
                        replyAction("알겠어요", NotificationActionReceiver.REPLY_OKAY, notifId),
                        replyAction("회의 중", NotificationActionReceiver.REPLY_BUSY, notifId),
                        replyAction("괜찮아요", NotificationActionReceiver.REPLY_DECLINE, notifId),
                    )
                },
            )
        } else {
            pushNotification(
                title = title,
                body = body,
                channelId = CHANNEL_COACHING,
                channelName = "키키 코칭 알림",
                channelDescription = "혈당 코칭 및 아침 브리핑 알림",
            )
        }
        ttsManager.playSound(resIdForAlertType(alertType))

        // 홈 대시보드(KikiSuggestionCard + 뱃지)용 — 모든 알림을 alertStream에 emit
        glucoseAlertManager.emitFcmAlert(title, body, alertType ?: "")

        // 채팅 메시지 FCM — KikiChatViewModel에 page=0 재조회 신호 전달
        if (message.data["chatMessageId"] != null) {
            healthRepository.emitChatFcmEvent()
        }
    }

    /**
     * 단일 알림 발행 헬퍼. showNotification / showMealFollowupNotification 의 공통부를 통합.
     *
     * - notifId 한 번 발급 → PendingIntent.requestCode 와 manager.notify(id) 양쪽에 같은 값 사용
     *   (옛 currentTimeMillis().toInt() 두 번 호출로 어긋나던 버그 회피)
     * - actionsBuilder 는 notifId 를 받아 액션 리스트를 만든다 — meal followup 같은 케이스에서
     *   EXTRA_NOTIF_ID 로 알림 본체 ID 를 전달해야 하기 때문.
     */
    private fun pushNotification(
        title: String,
        body: String,
        channelId: String,
        channelName: String,
        channelDescription: String,
        importance: Int = NotificationManager.IMPORTANCE_HIGH,
        priority: Int = NotificationCompat.PRIORITY_HIGH,
        actionsBuilder: (notifId: Int) -> List<NotificationCompat.Action> = { emptyList() },
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, importance)
                .apply { description = channelDescription },
        )

        val notifId = NotificationIds.nextEphemeral()
        val openApp =
            PendingIntent.getActivity(
                this,
                notifId,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.NAV_KIKI_ALARM_DETAIL)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .setPriority(priority)
        actionsBuilder(notifId).forEach { builder.addAction(it) }

        manager.notify(notifId, builder.build())
    }

    /** Meal followup 응답 액션 빌더 — notifId 를 broadcast intent extra 로 함께 전달. */
    private fun replyAction(
        label: String,
        reply: String,
        notifId: Int,
    ): NotificationCompat.Action {
        val intent =
            Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MEAL_REPLY
                putExtra(NotificationActionReceiver.EXTRA_USER_REPLY, reply)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
        val pi =
            PendingIntent.getBroadcast(
                this,
                NotificationIds.nextRequestCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Action(0, label, pi)
    }

    @androidx.annotation.RawRes
    private fun resIdForAlertType(alertType: String?): Int =
        when {
            alertType == null -> R.raw.kiki_morning
            alertType.startsWith("AGENT_WAKE_UP") -> R.raw.kiki_morning
            alertType.startsWith("AGENT_MEAL_FOLLOWUP") -> R.raw.kiki_walk
            alertType.startsWith("AGENT_MEAL_REPLY") -> R.raw.kiki_wait
            alertType.startsWith("AGENT_MEAL_RETRY") -> R.raw.kiki_stretch
            alertType.startsWith("AGENT_SLEEP_INSIGHT") -> R.raw.kiki_daily_done
            else -> R.raw.kiki_morning
        }

    companion object {
        private const val TAG = "FcmService"
        private const val CHANNEL_COACHING = "glucose_coaching"
        private const val ALERT_TYPE_MEAL_FOLLOWUP = "AGENT_MEAL_FOLLOWUP"
        private const val MEAL_FOLLOWUP_TITLE = "식후 컨디션"
    }
}
