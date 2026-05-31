package com.ssafy.s309.data.repository.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.ssafy.s309.data.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED

        /**
         * 오늘 자정 ~ 내일 자정까지의 일정을 반환한다.
         * 시연 시나리오용 — 제목에 사람 이름이나 약속 키워드가 있는 일정을 활용.
         */
        fun getTodayAndTomorrowEvents(): List<CalendarEvent> {
            val cal =
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
            val startOfToday = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_MONTH, 2)
            val endOfTomorrow = cal.timeInMillis
            return queryEvents(startOfToday, endOfTomorrow)
        }

        /** 지금부터 [days]일 이내의 일정을 반환한다. 권한 없으면 빈 리스트. */
        fun getUpcomingEvents(days: Int = 7): List<CalendarEvent> {
            val now = System.currentTimeMillis()
            return queryEvents(now, now + days * 24 * 60 * 60 * 1000L)
        }

        private fun queryEvents(
            fromMillis: Long,
            toMillis: Long,
        ): List<CalendarEvent> {
            if (!hasPermission()) return emptyList()

            val projection =
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                )
            val selection =
                "${CalendarContract.Events.DTSTART} >= ? AND " +
                    "${CalendarContract.Events.DTSTART} < ? AND " +
                    "${CalendarContract.Events.DELETED} = 0"
            val selectionArgs = arrayOf(fromMillis.toString(), toMillis.toString())

            val cursor =
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Events.DTSTART} ASC",
                ) ?: return emptyList()

            return buildList {
                cursor.use {
                    while (it.moveToNext()) {
                        add(
                            CalendarEvent(
                                id = it.getLong(0),
                                title = it.getString(1) ?: "",
                                startMillis = it.getLong(2),
                                endMillis = it.getLong(3),
                                allDay = it.getInt(4) != 0,
                                description = it.getString(5),
                                calendarName = it.getString(6),
                            ),
                        )
                    }
                }
            }
        }
    }
