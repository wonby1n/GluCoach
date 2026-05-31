package com.ssafy.s309.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ssafy.s309.wear.complication.MainComplicationService

class GlucoseListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Log.d("GlucoseService", "메시지 수신됨: path=${event.path}")
        when (event.path) {
            "/glucose" -> handleGlucose(event.data)
            "/notification" -> handleNotification(event.data)
        }
    }

    private fun handleGlucose(data: ByteArray) {
        val value = String(data, Charsets.UTF_8).toDoubleOrNull() ?: return
        Log.d("GlucoseService", "혈당값: $value")
        getSharedPreferences("glucose", Context.MODE_PRIVATE)
            .edit()
            .putFloat("latest", value.toFloat())
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, MainComplicationService::class.java))
            .requestUpdateAll()
    }

    private fun handleNotification(data: ByteArray) {
        val json =
            try {
                org.json.JSONObject(String(data, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e("GlucoseService", "알림 JSON 파싱 실패", e)
                return
            }
        val title = json.optString("title", "GluCoach")
        val body = json.optString("body")
        if (body.isEmpty()) return
        postNotification(title, body)
    }

    private fun postNotification(
        title: String,
        body: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("GlucoseService", "POST_NOTIFICATIONS 권한 없음 — 워치 알림 스킵")
            return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_KIKI) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_KIKI, "키키 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        enableVibration(true)
                        setSound(null, null)
                    },
            )
        }
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, CHANNEL_KIKI)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_KIKI = "kiki_watch_v1"
    }
}
