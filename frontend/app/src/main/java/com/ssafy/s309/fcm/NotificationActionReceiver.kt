package com.ssafy.s309.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ssafy.s309.data.local.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 식후 활동 알림의 3개 선택 버튼 탭을 처리하는 BroadcastReceiver.
 * 탭된 선택지를 AI 서비스의 /agent/post-meal 에 user_response 트리거로 전달한다.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MEAL_REPLY) return
        val userReply = intent.getStringExtra(EXTRA_USER_REPLY) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val userId = tokenManager.getUserId() ?: return

        if (notifId != -1) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notifId)
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                sendUserReply(userId, userReply)
            } catch (e: Exception) {
                Log.w(TAG, "식후 응답 전송 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun sendUserReply(
        userId: String,
        userReply: String,
    ) {
        val json =
            JSONObject().apply {
                put("user_id", userId)
                put(
                    "trigger",
                    JSONObject().apply {
                        put("reason", "user_response")
                        put("meal_time", "")
                        put("user_reply", userReply)
                    },
                )
            }.toString()

        val request =
            Request.Builder()
                .url("$AI_BASE_URL/agent/post-meal")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

        OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .close()
    }

    companion object {
        const val ACTION_MEAL_REPLY = "com.ssafy.s309.ACTION_MEAL_REPLY"
        const val EXTRA_USER_REPLY = "extra_user_reply"
        const val EXTRA_NOTIF_ID = "extra_notif_id"

        const val REPLY_OKAY = "알겠어요."
        const val REPLY_BUSY = "지금 회의 중이에요."
        const val REPLY_DECLINE = "괜찮아요."

        private const val AI_BASE_URL = "https://k14s309.p.ssafy.io/ai"
        private const val TAG = "MealReplyReceiver"
    }
}
