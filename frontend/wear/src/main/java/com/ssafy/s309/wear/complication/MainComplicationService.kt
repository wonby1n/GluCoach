package com.ssafy.s309.wear.complication

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class MainComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return buildComplication("95", "95 mg/dL")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val prefs = getSharedPreferences("glucose", Context.MODE_PRIVATE)
        val latest = prefs.getFloat("latest", -1f)

        return if (latest > 0) {
            buildComplication("${latest.toInt()}", "${latest.toInt()} mg/dL")
        } else {
            buildComplication("--", "혈당 없음")
        }
    }

    private fun buildComplication(
        text: String,
        description: String,
    ) = ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder(text).build(),
        contentDescription = PlainComplicationText.Builder(description).build(),
    ).build()
}
