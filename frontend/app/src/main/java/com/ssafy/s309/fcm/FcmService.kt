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
        showNotification(title, body)
        ttsManager.speak("$title. $body")
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
    }
}
