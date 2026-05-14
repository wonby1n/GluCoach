package com.ssafy.s309.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AlarmManager 가 발화하면 호출되어 식사 알림을 띄우고 다음날 같은 시각에 재예약한다.
 *
 * setExactAndAllowWhileIdle 은 1회성 알람이므로 매번 다시 걸어줘야 매일 반복된다.
 */
@AndroidEntryPoint
class MealReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: MealReminderManager

    @Inject lateinit var scheduler: MealReminderScheduler

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val slotName = intent.getStringExtra(EXTRA_SLOT) ?: return
        val slot = MealReminderManager.MealSlot.fromName(slotName) ?: return

        manager.showReminder(slot)
        scheduler.scheduleNext(slot)
    }

    companion object {
        const val EXTRA_SLOT = "meal_slot"
        const val ACTION_FIRE = "com.ssafy.s309.ACTION_MEAL_REMINDER_FIRE"
    }
}
