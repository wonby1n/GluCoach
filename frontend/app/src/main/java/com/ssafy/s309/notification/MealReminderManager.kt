package com.ssafy.s309.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ssafy.s309.MainActivity
import com.ssafy.s309.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealReminderManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ttsManager: TtsManager,
    ) {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            createChannel()
        }

        private fun createChannel() {
            // 사운드는 TtsManager 본문 발화로 대체했으므로 silent 채널로 생성한다.
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MEAL,
                    "식사 알림",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "아침·점심·저녁 식사 기록 리마인더"
                    setSound(null, null)
                },
            )
        }

        fun showReminder(slot: MealSlot) {
            val openCameraIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.NAV_FOOD_SCAN)
                }
            val cameraPending =
                PendingIntent.getActivity(
                    context,
                    slot.requestCode,
                    openCameraIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            notificationManager.notify(
                slot.notificationId,
                NotificationCompat.Builder(context, CHANNEL_MEAL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(slot.title)
                    .setContentText(slot.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(slot.body))
                    .setContentIntent(cameraPending)
                    .setAutoCancel(true)
                    .addAction(
                        R.drawable.ic_notification,
                        "사진 찍기",
                        cameraPending,
                    )
                    .build(),
            )

            ttsManager.speak(slot.body)
        }

        enum class MealSlot(
            val title: String,
            val body: String,
            val notificationId: Int,
            val requestCode: Int,
        ) {
            BREAKFAST(
                title = "🍳 아침 기록할 시간이에요",
                body = "오늘 아침은 뭘 드셨나요? 사진 한 장으로 빠르게 기록해보세요.",
                notificationId = 3001,
                requestCode = 30001,
            ),
            LUNCH(
                title = "🍱 점심 기록할 시간이에요",
                body = "오늘 점심은 뭘 드셨나요? 사진 한 장으로 빠르게 기록해보세요.",
                notificationId = 3002,
                requestCode = 30002,
            ),
            DINNER(
                title = "🍽️ 저녁 기록할 시간이에요",
                body = "오늘 저녁은 뭘 드셨나요? 사진 한 장으로 빠르게 기록해보세요.",
                notificationId = 3003,
                requestCode = 30003,
            ),
            ;

            companion object {
                fun fromName(name: String?): MealSlot? = entries.firstOrNull { it.name == name }
            }
        }

        companion object {
            const val CHANNEL_MEAL = "meal_reminder"
        }
    }
