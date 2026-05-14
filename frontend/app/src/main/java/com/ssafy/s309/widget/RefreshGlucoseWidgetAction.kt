package com.ssafy.s309.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback

/** 위젯 헤더의 ↻ 버튼 탭 시 즉시 데이터 fetch + 재렌더. */
class RefreshGlucoseWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        GlucoseWidgetUpdateWorker.enqueueImmediate(context)
    }
}
