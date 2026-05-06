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
import com.ssafy.s309.data.repository.UserRepository
import com.ssafy.s309.notification.GlucoseAlertManager
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

    @Inject lateinit var glucoseAlertManager: GlucoseAlertManager

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

        if (alertType == ALERT_TYPE_MEAL_FOLLOWUP) {
            showMealFollowupNotification(title, body)
        } else {
            showNotification(title, body)
        }
        glucoseAlertManager.emitFcmAlert(title, body)
    }

    private fun showMealFollowupNotification(
        title: String,
        body: String,
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MEAL, "식후 활동 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "식후 활동 유도 및 선택 응답 알림" },
        )

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
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        manager.notify(
            notifId,
            NotificationCompat.Builder(this, CHANNEL_MEAL)
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
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEFAULT, "기본 알림", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, CHANNEL_DEFAULT)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build(),
        )
    }

    companion object {
        private const val TAG = "FcmService"
        private const val CHANNEL_DEFAULT = "glucoach_default"
        private const val CHANNEL_MEAL = "glucose_coaching"
        private const val ALERT_TYPE_MEAL_FOLLOWUP = "AGENT_MEAL_FOLLOWUP"
        private const val MEAL_FOLLOWUP_TITLE = "식후 컨디션"
    }
}
