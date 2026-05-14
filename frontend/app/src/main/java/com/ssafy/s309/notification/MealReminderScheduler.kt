package com.ssafy.s309.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 매일 고정 시각(아침 08:00, 점심 12:30, 저녁 18:30)에 식사 리마인더를 예약한다.
 *
 * AlarmManager.setExactAndAllowWhileIdle 은 1회성이라 Receiver 가 발화 직후
 * 다음 24h 후 시각을 다시 걸어준다. 부팅 후에는 BootReceiver 에서 reschedule 한다.
 *
 * Android 12+ 의 SCHEDULE_EXACT_ALARM 권한 미보유 시 inexact 알람으로 폴백.
 */
@Singleton
class MealReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        /** 앱 부팅/시작 시 호출 — 3개 슬롯 모두 다음 발화 시각으로 예약. */
        fun scheduleAll() {
            MealReminderManager.MealSlot.entries.forEach { scheduleNext(it) }
        }

        /** Receiver 가 알림 발화 후 호출 — 24시간 뒤(다음 같은 시각)로 재예약. */
        fun scheduleNext(slot: MealReminderManager.MealSlot) {
            val triggerAtMs = nextTriggerMillis(slot)
            val pending = pendingIntentFor(slot)

            val canExact =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

            try {
                if (canExact) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pending,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pending,
                    )
                }
                Log.d(TAG, "예약 완료: $slot @ $triggerAtMs (exact=$canExact)")
            } catch (e: SecurityException) {
                Log.w(TAG, "exact alarm 권한 거부, inexact 로 폴백", e)
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            }
        }

        fun cancelAll() {
            MealReminderManager.MealSlot.entries.forEach { slot ->
                alarmManager.cancel(pendingIntentFor(slot))
            }
        }

        private fun pendingIntentFor(slot: MealReminderManager.MealSlot): PendingIntent {
            val intent =
                Intent(context, MealReminderReceiver::class.java).apply {
                    action = MealReminderReceiver.ACTION_FIRE
                    putExtra(MealReminderReceiver.EXTRA_SLOT, slot.name)
                }
            return PendingIntent.getBroadcast(
                context,
                slot.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun nextTriggerMillis(slot: MealReminderManager.MealSlot): Long {
            val target = TIMES.getValue(slot)
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(target)
            if (!next.isAfter(now)) {
                next = next.plusDays(1)
            }
            return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        companion object {
            private const val TAG = "MealReminderSched"

            // 고정 식사 시간 (사용자 설정 도입 전까지)
            private val TIMES =
                mapOf(
                    MealReminderManager.MealSlot.BREAKFAST to LocalTime.of(8, 0),
                    MealReminderManager.MealSlot.LUNCH to LocalTime.of(12, 30),
                    MealReminderManager.MealSlot.DINNER to LocalTime.of(18, 30),
                )
        }
    }
