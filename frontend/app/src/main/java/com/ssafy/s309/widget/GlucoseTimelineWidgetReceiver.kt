package com.ssafy.s309.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class GlucoseTimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlucoseTimelineWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 위젯이 처음 추가되거나 시스템이 update 요청 시 즉시 한 번 데이터 fetch.
        GlucoseWidgetUpdateWorker.enqueueImmediate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 첫 위젯 추가 시 주기 작업도 시작.
        GlucoseWidgetUpdateWorker.enqueuePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 모든 위젯 제거 시 주기 작업 해제.
        GlucoseWidgetUpdateWorker.cancelPeriodic(context)
    }
}
