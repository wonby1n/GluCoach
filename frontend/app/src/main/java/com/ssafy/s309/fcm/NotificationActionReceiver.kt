package com.ssafy.s309.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ssafy.s309.data.repository.HealthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 식후 활동 알림의 3개 선택 버튼 탭을 처리하는 BroadcastReceiver.
 * 백엔드 커맨드 엔드포인트를 경유해 DB 저장 + AI 디스패치를 단일 경로로 처리한다.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var healthRepository: HealthRepository

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_MEAL_REPLY) return
        val userReply = intent.getStringExtra(EXTRA_USER_REPLY) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)

        if (notifId != -1) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(notifId)
        }

        // 괜찮아요 — 서버 호출 불필요 (인앱 버튼과 동일한 동작)
        if (userReply == REPLY_DECLINE) return

        val displayLabel =
            when (userReply) {
                REPLY_OKAY -> "알겠어요"
                REPLY_BUSY -> "회의 중"
                else -> userReply
            }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                healthRepository.sendPostMealReply(userReply, displayLabel)
            } catch (e: Exception) {
                Log.w(TAG, "식후 응답 전송 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MEAL_REPLY = "com.ssafy.s309.ACTION_MEAL_REPLY"
        const val EXTRA_USER_REPLY = "extra_user_reply"
        const val EXTRA_NOTIF_ID = "extra_notif_id"

        const val REPLY_OKAY = "알겠어요."
        const val REPLY_BUSY = "지금 회의 중이에요."
        const val REPLY_DECLINE = "괜찮아요."

        private const val TAG = "MealReplyReceiver"
    }
}
