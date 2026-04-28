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

class FcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "토큰: $token")
        // TODO: 서버에 토큰 전송
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: "GluCoach"
        val body = message.notification?.body ?: ""
        showNotification(title, body)
    }

    private fun showNotification(
        title: String,
        body: String,
    ) {
        val channelId = "glucoach_default"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, "기본 알림", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
