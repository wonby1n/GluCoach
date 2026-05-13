package com.ssafy.s309.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ssafy.s309.data.api.HealthApi
import com.ssafy.s309.data.local.TokenManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 위젯용 혈당 데이터를 백엔드에서 받아와 통계 + 차트 비트맵을 만들고 위젯 상태에 저장한다.
 *
 * - 주기: 30분
 * - 트리거: 위젯 추가 / 새로고침 버튼 / 주기 알람
 */
@HiltWorker
class GlucoseWidgetUpdateWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val healthApi: HealthApi,
        private val tokenManager: TokenManager,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val isLoggedIn = tokenManager.getAccessToken() != null
            if (!isLoggedIn) {
                writeState(GlucoseWidgetState(isLoggedIn = false))
                refreshGlance()
                return Result.success()
            }

            val today = LocalDate.now()
            val from = today.atStartOfDay().format(ISO)
            val to = LocalDateTime.now().format(ISO)

            val records =
                runCatching { healthApi.getGlucoseRecords(from, to) }
                    .getOrElse {
                        writeState(GlucoseWidgetState(errorMessage = "데이터 조회 실패"))
                        refreshGlance()
                        return Result.retry()
                    }

            if (records.isEmpty()) {
                writeState(GlucoseWidgetState(updatedAtText = nowText()))
                refreshGlance()
                return Result.success()
            }

            val sorted = records.sortedBy { it.measuredAt }
            val values = sorted.map { it.value.toInt() }
            val current = values.last()
            val prev = values.dropLast(1).lastOrNull()
            val delta = prev?.let { current - it }
            val avg = values.average().roundToInt()
            val max = values.max()
            val min = values.min()
            val targetLow = 70
            val targetHigh = 140
            val inRange = values.count { it in targetLow..targetHigh }
            val inRangePct = ((inRange.toFloat() / values.size) * 100f).roundToInt()

            // 차트 비트맵을 PNG 로 디스크에 저장 (Glance state 가 직렬화 가능한 값만 받음)
            val bitmap =
                GlucoseChartRenderer.render(
                    readings = sorted,
                    widthPx = 600,
                    heightPx = 180,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                )
            val pngPath = saveChartPng(bitmap)

            writeState(
                GlucoseWidgetState(
                    currentValue = current,
                    trendDelta = delta,
                    updatedAtText = nowText(),
                    avg = avg,
                    max = max,
                    min = min,
                    inRangePct = inRangePct,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    chartPngPath = pngPath,
                    isLoggedIn = true,
                ),
            )
            refreshGlance()
            return Result.success()
        }

        private suspend fun writeState(state: GlucoseWidgetState) {
            val manager = GlanceAppWidgetManager(applicationContext)
            val ids = manager.getGlanceIds(GlucoseTimelineWidget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(applicationContext, GlucoseWidgetStateDefinition, id) { state }
            }
        }

        private suspend fun refreshGlance() {
            GlucoseTimelineWidget().updateAll(applicationContext)
        }

        private fun saveChartPng(bitmap: Bitmap): String {
            val dir = File(applicationContext.filesDir, "widget")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "glucose_chart.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return file.absolutePath
        }

        private fun nowText(): String {
            val now = LocalDateTime.now()
            return DateTimeFormatter.ofPattern("HH:mm").format(now)
        }

        companion object {
            private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            private const val UNIQUE_PERIODIC = "glucose_widget_periodic"
            private const val UNIQUE_IMMEDIATE = "glucose_widget_now"

            fun enqueuePeriodic(context: Context) {
                val req =
                    PeriodicWorkRequestBuilder<GlucoseWidgetUpdateWorker>(Duration.ofMinutes(30))
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req,
                )
            }

            fun enqueueImmediate(context: Context) {
                val req = OneTimeWorkRequestBuilder<GlucoseWidgetUpdateWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_IMMEDIATE,
                    ExistingWorkPolicy.REPLACE,
                    req,
                )
            }

            fun cancelPeriodic(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
            }
        }
    }
