package com.ssafy.s309.wear

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ssafy.s309.wear.complication.MainComplicationService

class GlucoseListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Log.d("GlucoseService", "메시지 수신됨: path=${event.path}")
        if (event.path != "/glucose") return
        val value = String(event.data, Charsets.UTF_8).toDoubleOrNull() ?: return
        Log.d("GlucoseService", "혈당값: $value")

        // 최신값 SharedPreferences에 저장
        getSharedPreferences("glucose", Context.MODE_PRIVATE)
            .edit()
            .putFloat("latest", value.toFloat())
            .putLong("updated_at", System.currentTimeMillis())
            .apply()

        // Complication 업데이트 요청
        ComplicationDataSourceUpdateRequester
            .create(this, ComponentName(this, MainComplicationService::class.java))
            .requestUpdateAll()
    }
}
