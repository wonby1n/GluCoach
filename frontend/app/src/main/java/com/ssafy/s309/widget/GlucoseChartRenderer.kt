package com.ssafy.s309.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.ssafy.s309.data.model.CgmRecordResponse
import java.time.Duration
import java.time.LocalDateTime

/**
 * 위젯용 혈당 라인 차트 비트맵 렌더러.
 *
 * Glance 는 Canvas / Compose drawing 을 지원하지 않으므로 호스트 프로세스에서
 * Bitmap 으로 그려 [Image] 로 전달한다.
 */
object GlucoseChartRenderer {
    /**
     * @param readings 오늘 혈당 시계열 (시간 오름차순 정렬 가정. 미정렬이어도 내부에서 정렬)
     * @param widthPx 비트맵 가로 픽셀
     * @param heightPx 비트맵 세로 픽셀
     * @param targetLow 목표 범위 하한
     * @param targetHigh 목표 범위 상한
     */
    fun render(
        readings: List<CgmRecordResponse>,
        widthPx: Int,
        heightPx: Int,
        targetLow: Int = 70,
        targetHigh: Int = 140,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT)

        if (readings.isEmpty()) {
            drawEmpty(canvas, widthPx, heightPx)
            return bmp
        }

        val sorted = readings.sortedBy { it.measuredAt }
        val paddingV = heightPx * 0.08f
        val plotTop = paddingV
        val plotBottom = heightPx - paddingV
        val plotHeight = plotBottom - plotTop

        // y축 범위: 데이터 최소·최대 ±20, 단 50~250 범위 안에 고정
        val values = sorted.map { it.value.toFloat() }
        val dataMin = values.min().coerceAtLeast(40f)
        val dataMax = values.max().coerceAtMost(300f)
        val yMin = (dataMin - 20f).coerceAtLeast(40f)
        val yMax = (dataMax + 20f).coerceAtMost(300f)
        val ySpan = (yMax - yMin).coerceAtLeast(1f)

        // x축 범위: 오늘 00:00 ~ 24:00 고정 (시간대 비교 일관성)
        val startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay()
        val xTotalMin = 24f * 60f

        fun timeToX(measuredAt: String): Float {
            val t = runCatching { LocalDateTime.parse(measuredAt) }.getOrNull() ?: return 0f
            val mins = Duration.between(startOfDay, t).toMinutes().toFloat()
            return (mins.coerceIn(0f, xTotalMin) / xTotalMin) * widthPx
        }

        fun valueToY(v: Float): Float {
            val ratio = (v - yMin) / ySpan
            return plotBottom - ratio * plotHeight
        }

        // 1. 목표 범위 배경 (연한 녹색)
        val targetTop = valueToY(targetHigh.toFloat()).coerceIn(plotTop, plotBottom)
        val targetBottom = valueToY(targetLow.toFloat()).coerceIn(plotTop, plotBottom)
        val bandPaint =
            Paint().apply {
                color = Color.argb(60, 134, 239, 172)
                isAntiAlias = true
            }
        canvas.drawRect(0f, targetTop, widthPx.toFloat(), targetBottom, bandPaint)

        // 2. 점선 (목표 상한/하한)
        val dashPaint =
            Paint().apply {
                color = Color.argb(140, 134, 239, 172)
                strokeWidth = 1f
                isAntiAlias = true
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
                style = Paint.Style.STROKE
            }
        canvas.drawLine(0f, targetTop, widthPx.toFloat(), targetTop, dashPaint)
        canvas.drawLine(0f, targetBottom, widthPx.toFloat(), targetBottom, dashPaint)

        // 3. 영역 그라데이션
        val areaPath = Path()
        val firstX = timeToX(sorted.first().measuredAt)
        val firstY = valueToY(sorted.first().value.toFloat())
        areaPath.moveTo(firstX, firstY)
        for (i in 1 until sorted.size) {
            val r = sorted[i]
            areaPath.lineTo(timeToX(r.measuredAt), valueToY(r.value.toFloat()))
        }
        val lastX = timeToX(sorted.last().measuredAt)
        areaPath.lineTo(lastX, plotBottom)
        areaPath.lineTo(firstX, plotBottom)
        areaPath.close()

        val areaPaint =
            Paint().apply {
                isAntiAlias = true
                shader =
                    LinearGradient(
                        0f, plotTop, 0f, plotBottom,
                        Color.argb(120, 14, 165, 233),
                        Color.argb(0, 14, 165, 233),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawPath(areaPath, areaPaint)

        // 4. 라인
        val linePath = Path()
        linePath.moveTo(firstX, firstY)
        for (i in 1 until sorted.size) {
            val r = sorted[i]
            linePath.lineTo(timeToX(r.measuredAt), valueToY(r.value.toFloat()))
        }
        val linePaint =
            Paint().apply {
                color = Color.rgb(14, 165, 233)
                strokeWidth = heightPx * 0.035f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
        canvas.drawPath(linePath, linePaint)

        // 5. 현재 포인트 (마지막 값)
        val lastY = valueToY(sorted.last().value.toFloat())
        val pointPaint =
            Paint().apply {
                color = Color.rgb(14, 165, 233)
                isAntiAlias = true
            }
        val pointRadius = heightPx * 0.06f
        canvas.drawCircle(
            lastX,
            lastY,
            pointRadius * 1.4f,
            Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
            },
        )
        canvas.drawCircle(lastX, lastY, pointRadius, pointPaint)

        return bmp
    }

    private fun drawEmpty(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
    ) {
        val paint =
            Paint().apply {
                color = Color.argb(120, 100, 116, 139)
                textSize = heightPx * 0.18f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("측정 데이터 없음", widthPx / 2f, heightPx / 2f, paint)
    }
}
