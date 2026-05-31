package com.ssafy.s309.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import kotlin.math.max

/**
 * "오늘 혈당 흐름" 카드 전체 컴포넌트.
 *
 * @param readings 시간순 정렬된 혈당 시리즈
 * @param range 사용자 목표 범위 (그래프 내부 회색 박스)
 * @param meals 식사 이벤트 (밥그릇 핀으로 표시)
 * @param hoursLabel 제목 우측 라벨 (예: "최근 6시간")
 * @param mealPinIcon 밥그릇 아이콘 asset — null 이면 원형 자리 표시자로 렌더
 * @param timeLabels X축 시간 라벨 (예: ["08:00","10:00","12:00","14:00"]). 빈 리스트면 렌더 생략.
 */
@Composable
fun GlucoseChartCard(
    readings: List<GlucoseReading>,
    range: GlucoseRange,
    modifier: Modifier = Modifier,
    isDeviceConnected: Boolean = false,
    hoursLabel: String = "최근 6시간",
    timeLabels: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .background(GlucoachColors.Surface)
                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "오늘 혈당 흐름",
                color = GlucoachColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = hoursLabel,
                color = GlucoachColors.Primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        if (readings.isNotEmpty()) {
            GlucoseChartBody(readings = readings, range = range)
            if (timeLabels.isNotEmpty()) {
                GlucoseChartTimeAxis(labels = timeLabels)
            }
        } else if (isDeviceConnected) {
            ConnectedWaiting()
        } else {
            DeviceNotConnected()
        }
    }
}

@Composable
private fun ConnectedWaiting() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = GlucoachColors.Primary,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "측정 대기 중",
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "혈당 데이터를 수신하고 있어요",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceNotConnected() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(GlucoachColors.Background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = GlucoachColors.Primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "기기가 연동되어 있지 않아요",
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "기기를 연동하면 혈당 흐름을 확인할 수 있어요",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GlucoseChartBody(
    readings: List<GlucoseReading>,
    range: GlucoseRange,
) {
    val dataMax = readings.maxOfOrNull { it.valueMgDl } ?: range.maxMgDl
    val dataMin = readings.minOfOrNull { it.valueMgDl } ?: range.minMgDl
    val padding = max(((dataMax - dataMin) * 0.15f).toInt(), 15)
    val yMax = dataMax + padding
    val yMin = dataMin - padding

    val minTime = readings.firstOrNull()?.timestampMillis ?: 0L
    val maxTime = readings.lastOrNull()?.timestampMillis ?: 1L
    val timeSpan = (maxTime - minTime).coerceAtLeast(1L)

    // Y 라벨 텍스트 높이를 실측 후 절반을 수직 정렬 보정값으로 사용.
    // 상수 대신 실측을 쓰는 이유: 시스템 폰트 스케일(1.3x/1.5x)이 적용돼도 회색 박스
    // 경계선과 라벨이 어긋나지 않도록 보장하기 위함.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val yLabelStyle = remember { TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    val yLabelHalfHeight =
        remember(textMeasurer, yLabelStyle, density) {
            with(density) {
                (textMeasurer.measure(text = "0", style = yLabelStyle).size.height / 2).toDp()
            }
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT),
    ) {
        // Canvas 와 동일한 수직 패딩을 사용해 Y라벨을 회색 박스 상/하단에 정렬한다.
        val canvasInnerHeight = maxHeight - CHART_TOP_PADDING - CHART_BOTTOM_PADDING
        val yRange = (yMax - yMin).toFloat().coerceAtLeast(1f)
        Text(
            text = "$dataMax",
            color = glucoseZoneColor(dataMax, range),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .offset(y = CHART_TOP_PADDING - yLabelHalfHeight)
                    .padding(start = 4.dp),
        )
        Text(
            text = "$dataMin",
            color = glucoseZoneColor(dataMin, range),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .offset(y = CHART_TOP_PADDING + canvasInnerHeight - yLabelHalfHeight)
                    .padding(start = 4.dp),
        )

        val lastPointColor =
            readings.lastOrNull()?.let { glucoseZoneColor(it.valueMgDl, range) }
                ?: GlucoachColors.Primary

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CHART_BODY_HEIGHT)
                    .padding(
                        start = CHART_X_START_PADDING,
                        end = CHART_X_END_PADDING,
                        top = CHART_TOP_PADDING,
                        bottom = CHART_BOTTOM_PADDING,
                    ),
        ) {
            val chartWidth = size.width
            val chartHeight = size.height

            fun yFor(value: Int): Float {
                val ratio = (value - yMin).toFloat() / (yMax - yMin).toFloat()
                return chartHeight * (1f - ratio.coerceIn(0f, 1f))
            }

            fun xFor(millis: Long): Float {
                val ratio = (millis - minTime).toFloat() / timeSpan.toFloat()
                return chartWidth * ratio.coerceIn(0f, 1f)
            }

            // 1) X축·Y축 기준선 (점선)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(
                color = GlucoachColors.ChartGrid,
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            drawLine(
                color = GlucoachColors.ChartGrid,
                start = Offset(0f, 0f),
                end = Offset(0f, chartHeight),
                strokeWidth = 1f,
                pathEffect = dash,
            )

            // 2) 혈당 라인 (Catmull-Rom 곡선)
            if (readings.size >= 2) {
                val pts = readings.map { Offset(xFor(it.timestampMillis), yFor(it.valueMgDl)) }
                val path =
                    Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            val p0 = pts[maxOf(i - 2, 0)]
                            val p1 = pts[i - 1]
                            val p2 = pts[i]
                            val p3 = pts[minOf(i + 1, pts.lastIndex)]
                            val cp1x = (p1.x + (p2.x - p0.x) / 6f).coerceIn(p1.x, p2.x)
                            val cp2x = (p2.x - (p3.x - p1.x) / 6f).coerceIn(p1.x, p2.x)
                            cubicTo(
                                cp1x,
                                p1.y + (p2.y - p0.y) / 6f,
                                cp2x,
                                p2.y - (p3.y - p1.y) / 6f,
                                p2.x,
                                p2.y,
                            )
                        }
                    }
                drawPath(
                    path = path,
                    color = GlucoachColors.Primary,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // 4) 마지막 포인트 강조 원
            readings.lastOrNull()?.let { last ->
                val cx = xFor(last.timestampMillis)
                val cy = yFor(last.valueMgDl)
                drawCircle(
                    color = lastPointColor,
                    radius = 9f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f),
                )
                drawCircle(
                    color = lastPointColor.copy(alpha = 0.2f),
                    radius = 9f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/** X축 시간 라벨 행 (예: 08:00 / 10:00 / 12:00 / 14:00) */
@Composable
fun GlucoseChartTimeAxis(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = CHART_X_START_PADDING,
                    end = CHART_X_END_PADDING,
                    top = 4.dp,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                color = GlucoachColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val CHART_BODY_HEIGHT = 130.dp
private val CHART_TOP_PADDING = 4.dp
private val CHART_BOTTOM_PADDING = 8.dp
private val CHART_X_START_PADDING = 28.dp
private val CHART_X_END_PADDING = 8.dp
private val MEAL_PIN_SIZE = 28.dp
